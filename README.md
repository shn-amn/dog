# HTTP access log monitor in Scala with FS2
An exercise for Datadog

## What exactly are we monitoring?
We want to read an HTTP access log file in realtime, and 
- every ten seconds, give some aggregated statistics of what has been going on;
- in the meantime, keep track of the total number of hits in the past two minutes,
  emit an alert when it crosses a certain threshold,
  and emit a recovery alert when it comes under the threshold again.

1. [Basic idea of the solution](#1)
2. [Why it wouldn't work](#2)
3. A better solution
4. Input
5. Output

## Why Scala and FS2?
For the past couple of years, I have been programming mostly in Scala.
However, during my phone screening, I did the code exercises in Go.

This reason was that I thought think that using functional programming language,
would hide the complexity of the interview question behind a `flatMap` and a `groupBy`,
and finer subtleties of a good solution would be lost.

For this exercise, however, I thought I could go with Scala.

**FS2**, "Functional Streams for Scala", 
is a library of purely functional but effectful streaming based on Cats.
I found their approach very "civilised"
and I very much enjoy using this library.

I also chose FS2 because it is a young library 
and does not always have all the tools that one might need.
I thought it might give me the chance of doing some low-level IO programming --
and it did.
As will see below, I ended up writing code that I should contribute back to FS2 file pacakge. 

<a name="1"></a>
# 1  Basic idea of the solution
Now back to the problem at hand.

Let's forget for a minute about reading and parsing the input, 
and suppose we have a stream of parsed logs coming through.
The basic sketch of my solution is the following:

```scala
  import java.time.Instant
  
  import cats.effect._
  import cats.implicits._
  import fs2._
  
  case class Log(timestamp: Instant, section: String)
  
  def monitoring(threshold: Int): Pipe[IO, Log, Unit] = _
    .groupAdjacentBy(_.timestamp.toEpochMilli / 10000)           // group in 10 sec chunks
    .map(_._2.toVector groupBy (_.section) mapValues (_.length)) // hits by section
    .evalTap(stats => IO(print(s"\r$stats")))
    .sliding(12)                                                 // 2 min = 12 * 10 sec
    .map(_ flatMap (_.values) sum)                               // all hits in 2 min
    .map(_ > threshold * 120)                                    // 2 min = 120 sec
    .filterWithPrevious(_ != _)                                  // detect change in trafic status (= alerts)
    .evalMap(busy => IO(print(if (busy) "\rToo busy.\n" else "\rNormal.\n")))
```

I'll go through the solution line by line shortly, 
but first let me admit that **this is an opinionated solution
based on certain assumptions** about the input;
most importantly that the input is ordered in time.

### _Assumption: ordered input_
What I mean by the input being ordered is that
the logs that appear earlier in the file have an earlier timestamp.
  
Given the nature of logs, this is a reasonable assumption.
Even if the logs were written concurrently, 
given the low resolution of timestamps (1 second),
there is very little chance that an _earlier_ log (by timestamp)
might be written _later_ than a log having a bigger timestamp. 

Assuming the logs are ordered, here is how the code works:

### 1.1 Aggregating statistics

```scala
.groupAdjacentBy(_.timestamp.toEpochMilli / 10000)
```
The consecutive logs whose timestamps are in the same 10 second window
are grouped together in chunks. 
The `Stream[_, Log]` is transformed into a `Stream[_, (Long, Chunk[Log])]`.

How does it work? 
`_.timestamp.toEpochMilli` is the number of milliseconds from 
1 Jan 1970 00:00:00 to the time stamped on the log.
`_.timestamp.toEpochMilli / 10000` is therefore the number of 
seconds from _Linux epoch_ to timestamp divided by 10.
This value is the same for all timestamps between `__:__:_0` to `__:__:_9`
on any given day, hour and minute.

The element `(0L, Chunk(...))`, for example,
contains all the logs dating from 1 Jan 1970 between midnight and 00:00:10.
The element `(155963740L, Chunk(...))`, 
contains the logs written on 4 Jun 2019 between 9:36:40 am to 9:36:49.

```scala
.map(_._2.toVector groupBy (_.section) mapValues (_.length))
```
Then the `Long` value of the tuple is discarded, 
and logs are grouped by sections, 
to each section assigned the number of hits it has received.
Now we have a `Stream[_, Map[String, Int]]` 
that corresponds to the basic 10 sec statistics.

```scala
.evalTap(stats => IO(print(s"\r$stats")))
```
This does not modify the stream but, 
as an effect, prints all the elements that pass through.

(On a Unix-like terminal, using carriage return `\r` without new line `\n`
is an easy way of writing _transient_ data that will later be overwritten.
But more on displaying output later.)

So much for the first part of the problem, the statistics!
Since we do not persist statistics, 
now we could just drain our `Stream[_, Map[String, Int]]` away
and throw away the results.
But that sounds like a waste...

Couldn't we put these aggregated statistics to a use?

### 1.2 Recycling statistics to trigger alerts
We could, of course, scan the stream of parsed logs directly to trigger alerts.
But it so happens that 
the 2 minute period for aggregating alerts is an exact multiple of 
the 10 second period for aggregating statistics.
Now that we have the statistics, we can instead group every 12 of them
and we'll have exactly what we need to inspect for alerting situations.

That's what the next lines of code do.

```scala
.sliding(12)
```
This line buffers statistics in "sliding" dozens: 
it emits the first twelve statistics together,
then from 2nd to 13th, from 3rd to 14th and so on.

We're grouping every 12 statistics, using the fact that
12 periods of 10 seconds make for a period of 2 minutes.

The result is a `Stream[_, Queue[Map[String, Int]]]`
where the queues are of size 12.

```scala
.map(_ flatMap (_.values) sum)
```
Then we add up all the hit counts 
to have the total number of hits in 2 minute windows
`00:00:00 - 00:02:00`, `00:00:10 - 00:02:10`, `00:00:20 - 00:02:20` 
and so on.
The result is thus a `Stream[_, Int]`.

```scala
.map(_ > threshold * 120)
``` 
We compare these counts to the threshold.
We now have a `Stream[_, Boolean]` which is 
`true` when the past two minutes have been _busy_
and `false` when they have been _normal_.

Alerts are triggered when we go from a normal to a busy period.
Recovery alert are triggered inversely
when we go from a busy period to a normal one.
The following line detects these changes:

```scala
.filterWithPrevious(_ != _)
``` 
This line lets through only values that are different from the previous,
i.e. values that represent in change in activity status.

We still have a `Stream[_, Boolean]`, except that now
every `true` represents an alert and every `false` a recovery.

```scala
.evalMap(busy => IO(print(if (busy) "\rToo busy.\n" else "\rNormal.\n")))
```
We print out the alerts and recoveries, and we are done!

(Here we use new lines `\n` to have _permanent_ rather than transient lines.)

Easy, wasn't it? Not so soon...

<a name="2"><a/>
# 2 Why it wouldn't work
Have you noticed that we have not used any clocks or timers?
Never! Not once! 
Is it not strange for an apparently time-based application?

Well, we have used log timestamps instead.
To trust timestamps and not to use the clock was a deliberate choice, 
and it has a few advantages.

For one thing, clocks are both effect and state.
Avoiding them makes the programme more robust, and more easily testable.
Furthermore, use of clocks (and waits) does not yield exact results,
whereas our approach, when it works, is totally accurate.
Also, I find this solution more elegant than a timer-based solution.

And it _works!_ 
Well, under some conditions. 
It works for servers that are reasonably popular and busy.
It "essentially" works for any server that has 
_at least one hit every 10 seconds_.
And it works perfectly for any server that has 
at least one hit every second or two. 

But what if the server is less busy...

## _Problem: sparse logs_
Lets see what happens when we are processing the following stream of logs:

```
127.0.0.1 - john [09/May/2018:16:00:21 +0000] "GET /report HTTP/1.0" 200 123
127.0.0.1 - jill [09/May/2018:16:00:22 +0000] "GET /api/user HTTP/1.0" 200 234
127.0.0.1 - brad [09/May/2018:16:00:37 +0000] "POST /api/user HTTP/1.0" 200 34
127.0.0.1 - mary [09/May/2018:16:00:51 +0000] "POST /api/user HTTP/1.0" 503 12
127.0.0.1 - jane [09/May/2018:16:00:56 +0000] "POST /api/user HTTP/1.0" 503 12
```

The first two logs (John and Jill) are grouped together, the group of `16:00:2_`,
the Brad makes the group of `16:00:3_` and Mary and Jane the group of `16:00:5_`.

The group of `16:00:4_` is missing!

This means that 
- there will be no statistics for 16:00:40 to 16:00:49
- the following alerts, which are based on these statistics, are messed up.

On a more extreme case, on a day when the server receives no calls,
there will be no logs, and _no statistics at all!_








### Edge problem: first alerts