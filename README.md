# An HTTP access log monitor in Scala with FS2
An exercise for Datadog

## What exactly are we monitoring?
We want to read an HTTP access log file in realtime, and 
- every ten seconds, give some aggregated statistics of what has been going on;
- in the meantime, keep track of the total number of hits in the past two minutes,
  emit an alert when it crosses a certain threshold,
  and emit a recovery alert when it comes under the threshold again.

### Contents
1. [Basic idea of the solution](#1)
2. [Why it wouldn't work](#2)
3. [Refined solution](#3)
4. [Input](#4)
5. [Output](#5)
6. [Tests](#6)
7. [Room for improvement](#7)
8. [Why Scala and FS2?](#8)

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

### 2.1 Problem: sparse logs
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

This is obviously not acceptable: 
the application being a real-time monitor we want to see a report every ten seconds,
whether or not any hits have actually be received. 
At the same time we don't want to lose the advantages and the elegance 
of basing our statistics on _stamped time_ of the logs rather than clock time.

So we why not a hybrid approach...

### 2.2 Solution to the problem: add a heartbeat
So what we do is to add to our log stream, a stream of 1-second ticks
to keep the monitor awake.
These ticks, or _heartbeats_, do have a timestamp, but contain no logs.
So they do not change the actual data being processed, 
but just remind the monitor of passing of times.
Also, for the results to be exact, 
the timing of the heartbeats does not have to be accurate, 
because the results still depend on the timestamps of actual logs.

Now we have the best of two worlds!

### Caution
There is still a little problem.
Logs do take a little time to be written and a little time to read.
Heartbeats, on the other hand, are generated inside our application and are almost instantaneous.
If heartbeat of a certain timestamp, say `14:00:00`, 
arrives before an actual log with an earlier timestamp, say `13:59:50`,
this will violate our assumption of _ordered input_ 
and disturb our groupings, stats and alerts.

To avoid this, we backdate the heartbeats with a little lag, lets say 500ms.
This will provide enough time for the actual logs to catch up.
So the heartbeat emitted at 14:00:00 in the example above, 
will be stamped at `13:59:59` and will not violate the order.
We also filter out the heartbeats with a timestamp before an actual log already arrived.
So, for example, if a log stamped `14:00:00` has already arrived
before the heartbeat stamped `13:59:59`, we will just ignore the heartbeat.
In this way, the order of the input is not violated.

In my experience, a 500ms lag proved amply sufficient.
But I leave it configurable.

<a name="3"></a>
# 3 Refined solution
So our final stream, looks more like this:

```scala
parseLogs(path = logfile, since = start, blockingEC)
  .through(addHeartbeat(lag = 500 millis))
  .through(groupToStats)
  .evalTap(stats => IO(Display transient stats.message))
  .through(scanForAlerts(threshold))
  .evalMap(signal => IO(Display permanent (signal.message, signal.color)))
```

where 
- `parseLogs` reads the input file, parses the logs, 
  and ignores whatever has happened before the start of the application.
  More on this in the [input section](#4).
- `addHeartBeat` adds a heartbeat with the given lag and filters out the late heartbeats.
- `groupToStats` group the statistics and computes the required aggregations, 
  pretty much as discussed above.
- `scanForAlerts` detects changes in traffic status 
  and emits appropriate alert and recovery signals.
  In addition, it also takes care of an edge condition
  where the first signal is a recovery (meaning the first two minutes have been calm) 
  without any prior alert.
  This signal is the suppressed.
- `Display.transient` and `Display.permanent` take care of writing to console.
  More on that in the [output section](#5).

<a name="4"></a>
# 4 Input

### 4.1 The workflow
Parsing the input was straight-forward:

```scala
import one.shn.dog.in.File

File
  .readIndefinitely(path, blockingEC, 1024, 250 millis)
  .through(text.utf8Decode)
  .through(text.lines)
  .mapFilter(Log.parse)
  .dropWhile(_.timestamp isAfter since)
```

I thought that 
we read the file indefinitely in a `tail -f` fashion,
decode it and separate it into lines,
then parse the logs ignoring any line that is not a log,
and finally throw away whatever happened before the start of our application.

Then, I noticed there was no `.readIndefinitely` function in FS2's file library!

<a name="tail-f"></a>
##### Surprise: no library function for `tail -f` in FS2!
That meant that I had to write the function myself, 
and that was actually good news.

It wouldn't be fun to do an essentially I/O app 
without doing some low-level blocking I/O, 
would it?

So I looked how the file library was written, and added the function.
I have not yet contributed it back to FS2, 
but if it is a useful feature, why not.

My code here uses the underlying structure of FS2, 
and I don't think it would be interesting going through it here.
But the code is there, if anyone is interested.

### 4.2 The parsing
We use a simple regexps to parse the logs
and I only keep the timestamp and the section 
as strictly necessary for the exercise as stated:

```regexp
^[\d.:]+\s\S*-\S*\s\S*\s\[([^\]]+)\]\s"\S*\s(/\S*)\S*\s\S*"\s\d{3}\s\d+
```

However, if need be, other field can be extracted 
almost as easily as adding parentheses.

##### Fun fact: "May" has three letters!
It's actually the only month on the calendar with only three letters,
and all the four examples provided with the exercise where logs of May 2018!

The problem is, when you see `09/May/2018`, you can't immediately guess 
whether for November it will be `November` or `Nov`.
I figured it must have been intentional, 
and so went for a parser that accepts both.
It seems, though, that it actually is `Nov` and not the full version.

<a name="5"></a>
# 5 Output
I have never written an application with serious console output before,
and this was the part that actually took me the most research!

What I ended up doing, was to make a `Display` object, 
which is essentially a wrapper of standard output
that keeps a queue of the last _messages_ received.

Any _stats_, _alert_, or _recovery_ signal is first received in the queue.
The queue has a maximum capacity of 10 messages.

All that is in the queue is shown in a first in on top order.

If the queue is on maximum capacity and a new message is received,
the topmost (oldest) message in the queue will be dequeued.

If this message is _transient_ (stats), it will just be forgotten, 
and the 9 remaining messages plus the new one will be shown.

If, however, the old message is _permanent_ (alert or recovery),
It will be permanently written on top (sticks to its place) 
followed by the 9 remaining messages and the newly queued one.

After a while, the display could look like this, 
with alerts in red and recoveries in green:
```
17:49:00 High traffic alert! 1475 hits in past 2 minutes.
17:50:20 Back to normal traffic. 1096 hits in past 2 minutes.
17:57:10 No hits.
17:57:20 29 hits. Most popular section /report with 100%.
17:57:30 212 hits. Most popular section /report with 100%.
17:57:40 204 hits. Most popular section /report with 100%.
17:57:50 207 hits. Most popular section /report with 100%.
17:58:00 197 hits. Most popular section /report with 100%.
17:58:10 216 hits. Most popular section /report with 100%.
17:58:20 199 hits. Most popular section /report with 100%.
17:58:20 High traffic alert! 1264 hits in past 2 minutes.
17:58:30 193 hits. Most popular section /report with 100%.
```

The tests contain a `out/DisplayTest` which is actually a worksheet,
and it is fun to run to see how the display works.

<a name="disp"></a>
### Display stability
This display is not very robust.
I noticed that when a message takes more than one line 
to display on console, it disrupts the display.
It actually made me shorten my alert messages!

<a name="6"></a>
# 6 Tests
- The alerting logic is pretty thoroughly tested in StreamsSpec, 
  as required but the exercise.
- There is a worksheet `DisplayTest` (not actually a test) in `src/test/.../out`
  which I enjoyed watching, and for a moment only, made me proud of my display!
- The other tests were basically for me, 
  for when I wanted to make sure that everything worked as intended.
  They are _not_ thorough or conclusive.

<a name="7"></a>
# 7 Room for improvement
### 7.1 Edge conditions
Suppose that the threshold is 20 hits per second, 
and as the app starts, in the first 10 seconds, 
we already receive 2500 hits.
That is bigger that the threshold for two minutes (120 * 20 = 240)
and we can already raise the alarm.

This feature is not yet supported: 
the app will still wait 1 minute and 50 seconds before raising an alarm!

### 7.3 Display
- The persistent part of the page contains signals 
  in an order of `alert1-recovery1-alert2-recovery2-...`.
  It would be nice to just append the line when an alert is recovered, 
  rather than just adding a recovery line below.
- [Stability](#disp) can be improved.

### 7.2 Cleanness
`Display` and `config` both are stateful and effectful.
As such, they should be treated as _resources_ in our main stream,
but as-is, they are just simple objects.
This was to keep the code simple and more readable.
But ideally this should be treated properly.

### 7.3 General issues
- **Error handling**: exceptions should be caught and reported properly.
  This is not yet the case.
  Event when environment variables cannot be parsed or the file does not exist
  the actual exception is thrown and ends the programme.
- More **tests** can't hurt!  

### 7.4 Finally
I am sure you can find more!

<a name="8"></a>
# 8 Why Scala and FS2?
For the past couple of years, I have been programming mostly in Scala.

However, during my phone screening, I did the code exercises in Go.
The reason was that I thought think that using functional programming language,
would hide the complexity of the interview question behind a `flatMap` and a `groupBy`,
and finer subtleties of a good solution would be lost.

For this exercise, I thought I could go with Scala, 
and try to profit from its expressiveness and brevity
when the problem at hand is somewhat more complex.

FS2, "Functional Streams for Scala", 
is a library of purely functional but effectful streaming based on Cats.
I find their approach very "civilised" and I very much enjoy using this library.

I also chose FS2 because it is a young library 
and does not always have all the tools that one might need.
I thought it might give me the chance of doing some low-level IO programming --
and [it did](#tail-f).
 