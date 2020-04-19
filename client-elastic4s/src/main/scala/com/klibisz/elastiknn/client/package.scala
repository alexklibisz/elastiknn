package com.klibisz.elastiknn

import scala.concurrent.Future

package object client {
  type ElastiknnFutureClient = ElastiknnClient[Future]
}
