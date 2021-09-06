package com.elastiknn.annb.example

object Example {
  sealed trait A {
    type T
  }

  object A {
    case object A1 extends A {
      type T = Int
    }
    case object A2 extends A {
      type T = String
    }
  }

  sealed trait B {
    type T
    def apply(t: T): Unit
  }

  object B {
    type Aux[TT] = B { type T = TT }

    def apply[AA <: A](a: AA)(implicit ev: Instance.Aux[AA]): Aux[AA#T] =
      ev.instance

    private sealed trait Instance {
      type AA <: A
      def instance: B.Aux[AA#T]
    }

    private object Instance {
      type Aux[AAA <: A] = Instance { type AA = AAA }

      implicit final val A1Instance: Instance.Aux[A.A1.type] =
        new Instance {
          override type AA = A.A1.type
          override val instance: B.Aux[AA#T] =
            new B {
              override type T = AA#T
              override def apply(i: T): Unit = println(i + 1)
            }
        }

      implicit final val A2Instance: Instance.Aux[A.A2.type] =
        new Instance {
          override type AA = A.A2.type
          override val instance: B.Aux[AA#T] =
            new B {
              override type T = AA#T
              override def apply(s: T): Unit = println(s.toUpperCase)
            }
        }
    }
  }

  def main(args: Array[String]): Unit = {
    val a1b = B(A.A1)
    a1b(99)

    val a2b = B(A.A2)
    a2b("ninety nine")
  }
}
