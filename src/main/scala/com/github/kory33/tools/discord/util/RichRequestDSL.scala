package com.github.kory33.tools.discord.util

import net.katsstuff.ackcord.RequestDSL

object RichRequestDSL {
  implicit class RichRequestDSL[+A](requestDSL: RequestDSL[A]) {
    def andThen[B](another: RequestDSL[B]): RequestDSL[B] = requestDSL.flatMap(_ => another)
  }
}
