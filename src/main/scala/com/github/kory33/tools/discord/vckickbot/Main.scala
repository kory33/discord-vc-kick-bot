package com.github.kory33.tools.discord.vckickbot

import com.github.kory33.tools.discord.util.TokenReader
import net.katsstuff.ackcord.ClientSettings

import scala.concurrent.ExecutionContext.Implicits.global

object Main {

  def main(args: Array[String]): Unit = {
    val token = TokenReader
      .fromEnvironment("BOT_TOKEN")
      .getOrElse(throw new RuntimeException("BOT_TOKEN environment variable is not found"))

    ClientSettings(token, commandSettings = VCKickBotCommandSettings.commandSettings)
      .build()
      .flatMap(_.login())
  }

}
