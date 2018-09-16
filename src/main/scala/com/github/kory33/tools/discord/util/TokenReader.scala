package com.github.kory33.tools.discord.util

object TokenReader {

  def fromEnvironment(variableName: String): Option[String] = sys.env.get(variableName)

}
