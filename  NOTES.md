# NOTES

## Decisions

- **User and posts are fetched concurrently.**
- **Two separate clients** call the two services.
- **The user is mandatory, posts are optional.**
- **Posts unavailable:** if the posts service fails or is too slow, the response contains
  `posts.status = UNAVAILABLE`. This tells the caller that there is no information about the posts,
  which is different from a user who has no posts.
- **Total timeout for posts: 1.5 s, including the retry.** The latency of the summary service
  doesn't depend on the slowest case of another service.
- **One retry for 5xx and connection failures.** This reduces the impact of the ~10 % failures.
  Timeouts, 4xx and invalid responses are not retried.

## Deliberately left out

- **Circuit breaker:** define which failures should open the circuit breaker for the posts service.
- **Cache for posts:** returning cached posts would be better than `UNAVAILABLE`.
- **Retry for the users service:** the task only describes the posts service as unreliable.
- **Metrics, authentication.**

## What I'd do next

1. Add a circuit breaker per service.
2. Count responses with UNAVAILABLE posts and alert when the rate rises, so a failing posts service is noticed.
3. Cache the last loaded posts
4. Set the timeouts based on measured latencies of the posts service.

## Where the AI helped / got it wrong

**Where the AI helped:**
- Creating files from precise instructions, especially unit tests, configuration files and Javadoc.
- Adding the required dependencies without me having to look them up.
- Pointing out missing logging and exception handling.

**Where the AI got it wrong:**
- It hard-coded configuration such as the base URLs in Java instead of putting it in `application.yml`.
- It needs precise instructions, e.g. for the configuration and dependencies. Otherwise it adds a lot
  of things that aren't needed and are hard to understand.