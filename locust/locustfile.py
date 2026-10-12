"""Batch submission and position lookup against a running Position Book API."""

from uuid import uuid4

from locust import HttpUser, between, events, task
from locust.exception import StopUser


@events.init_command_line_parser.add_listener
def add_options(parser):
    parser.add_argument(
        "--batch-size", type=int, default=10,
        help="Events per request, must not exceed the application's batch limit.",
    )
    parser.add_argument(
        "--position-mode", choices=["separate", "shared"], default="separate",
        help="Use a separate account per user or one position shared by all users.",
    )
    parser.add_argument(
        "--context-path", default="/position-book/v1",
        help="Application context path.",
    )


class PositionBookUser(HttpUser):
    wait_time = between(0.1, 0.3)

    def on_start(self):
        options = self.environment.parsed_options
        if options.batch_size < 1:
            raise ValueError("Batch size must be positive")

        self.batch_size = options.batch_size
        self.shared_position = options.position_mode == "shared"
        self.account_id = "PERFSHARED" if self.shared_position else "PERF" + uuid4().hex
        self.security_id = "PERFSEC1"
        self.context_path = options.context_path.rstrip("/")
        self.expected_quantity = 0

    @task
    def submit_and_retrieve(self):
        # Random positive IDs fit Java Long and avoid ordinary replay collisions.
        batch = [
            {
                "id": (uuid4().int % ((1 << 63) - 1)) + 1,
                "action": "BUY",
                "accountId": self.account_id,
                "securityId": self.security_id,
                "quantity": 1,
            }
            for _ in range(self.batch_size)
        ]

        with self.client.post(
            self.context_path + "/trade-events",
            json=batch,
            headers=self.request_headers(),
            name="POST /trade-events",
            catch_response=True,
            timeout=10,
        ) as response:
            try:
                if response.status_code != 200:
                    raise ValueError(f"Unexpected HTTP status: {response.status_code}")

                body = response.json()
                results = body["results"]
                if body["processed"] != self.batch_size or body["rejected"] != 0:
                    raise ValueError("Batch contains rejected or missing events")
                if len(results) != self.batch_size:
                    raise ValueError("Unexpected result count")
                for index, result in enumerate(results):
                    if (
                        result["index"] != index
                        or result["eventId"] != batch[index]["id"]
                        or result["eventStatus"] != "PROCESSED"
                        or result.get("errors")
                    ):
                        raise ValueError("Unexpected event outcome")
            except (ValueError, KeyError, TypeError) as error:
                response.failure(str(error))
                # The response may be uncertain after a partial update.
                # Stop this user rather than making misleading quantity assertions.
                raise StopUser() from error

        self.expected_quantity += self.batch_size

        with self.client.get(
            self.context_path + "/positions",
            params={"accountId": self.account_id, "securityId": self.security_id},
            headers=self.request_headers(),
            name="GET /positions",
            catch_response=True,
            timeout=10,
        ) as response:
            try:
                if response.status_code != 200:
                    raise ValueError(f"Unexpected HTTP status: {response.status_code}")

                body = response.json()
                quantity = body["quantity"]
                if body["accountId"] != self.account_id or body["securityId"] != self.security_id:
                    raise ValueError("Position identifiers do not match")
                if type(quantity) is not int:
                    raise ValueError("Position quantity is not an integer")
                if self.shared_position:
                    # Other users can buy between our POST and GET.
                    if quantity < self.expected_quantity:
                        raise ValueError("Shared position is below this user's accepted quantity")
                elif quantity != self.expected_quantity:
                    raise ValueError(
                        f"Expected quantity {self.expected_quantity}, received {quantity}"
                    )
            except (ValueError, KeyError, TypeError) as error:
                response.failure(str(error))
                raise StopUser() from error

    @staticmethod
    def request_headers():
        return {
            "Accept": "application/json",
            "x-jpmc-request-id": str(uuid4()),
        }
