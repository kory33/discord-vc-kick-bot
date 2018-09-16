package com.github.kory33.tools.discord.util

import scala.concurrent.{ExecutionContext, Future}

object RichFuture {
  implicit class RichFuture[S](future: Future[S]) {
    def applyForeach[U](fn: S => U)(implicit executionContext: ExecutionContext): Future[S] = {
      future.map(s => { fn(s); s })
    }
  }
}
