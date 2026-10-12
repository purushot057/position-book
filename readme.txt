ASSUMPTIONS AND RATIONALE

1. Payload format
   The tables provided in exercise are treated as examples. Requests and responses use JSON,
   with an array of events for batch submission.

2. Event IDs
   BUY/SELL IDs are globally unique so cancellation identifies one original
   trade. Duplicate submissions are rejected to prevent double counting.
   IDs cannot be reused after cancellation to keep each ID associated with one original trade.

3. Cancellation
   The account must match the original trade to avoid cancelling another
   account's trade. Security and quantity are ignored as stated in the brief.
   Unknown and repeated cancellations are rejected because there is no
   active trade to reverse.

4. Negative and zero positions
   Negative positions are disabled by default because short selling is not
   specified. This is configurable and applies to cancellation too.
   Zero positions are retained so an existing position remains retrievable.

5. Batch processing
   The batch limit defaults to 100 events to bound processing work and response size per request.
   The brief specifies no limit, so this is configurable; larger streams can be submitted in batches.
   Invalid events are rejected individually so valid events can continue.
   Unreadable requests, empty batches and oversized batches are rejected before processing.

6. Input validation
   Identifier rules are assumed because the brief specifies no format:
   account Ids allow 1–64 ASCII letters or digits; security Ids allow 1–128
   ASCII letters, digits, dots, underscores or hyphens.
   BUY/SELL quantities and event IDs must be positive Long values.
   Arithmetic overflow is rejected to prevent incorrect quantities.

7. Position retrieval
   GET returns the current quantity only. The examples also show drilldown;
   accepted trades and cancellations are retained internally, but history
   retrieval is treated as outside the two-endpoint scope.

8. Ordering and failures
   Events follow input order within a batch. Concurrent requests may
   interleave between events. Batches are not transactional, an unexpected
   failure stops processing without rolling back earlier successes.
   Retrying accepted IDs returns duplicate rejections.

The design approach and run instructions are in README.md.