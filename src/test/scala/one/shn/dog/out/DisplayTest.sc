import one.shn.dog.out.Display

def square(n: Int) = n * n
def isSquare(n: Int) = square(0 to n takeWhile (i => i * i <= n) last) == n

def test: Unit = {
  1 to 100 foreach { n =>
    Thread sleep 500
    if (isSquare(n)) Display permanent s"$n is a perfect square."
    else Display transient s"$n is not perfect. As we all."
  }
  Display close "Yay!"
}

test
