package com.github.kory33.tools.discord.vckickbot

import cats.Monad
import com.github.kory33.tools.discord.util.RichFuture._
import net.katsstuff.ackcord.commands.{CmdCategory, CmdDescription, CmdFilter, ParsedCmd}
import net.katsstuff.ackcord.util.Streamable
import net.katsstuff.ackcord._
import net.katsstuff.ackcord.data.ChannelType
import net.katsstuff.ackcord.http.rest._

import scala.concurrent.ExecutionContext.Implicits.global

class VCKickUsersHandler[F[_]: Monad: Streamable] extends (ParsedCmd[F, List[String]] => Unit) {

  override def apply(command: ParsedCmd[F, List[String]]): Unit = {
    import RequestDSL._
    implicit val cache: CacheSnapshot[F] = command.cache

    val message = command.msg

    // bot itself might be included, but does not matter since the bot does not join vc
    val targetUserIds = message.mentions.toSet

    for {
      guildChannel <- message.tGuildChannel
      guild <- guildChannel.guild

      intermediaryVCData = CreateGuildChannelData("vckickbot-intermediary", RestSome(ChannelType.GuildVoice))
      intermediaryVC <- CreateGuildChannel(guild.id, intermediaryVCData)
      modifyTargetData = ModifyGuildMemberData(channelId = RestSome(intermediaryVC.id))

      memberCollectionResult <- for {
        member <- guild.members.values if targetUserIds.contains(member.userId)
      } yield for {
        result <- ModifyGuildMember(guild.id, member.userId, modifyTargetData)
      } yield result

      _ <- memberCollectionResult.flatMap(_ => DeleteCloseChannel(intermediaryVC.id))
    } yield ()
  }

}

object Main {

  def fromEnvironment(variableName: String): Option[String] = sys.env.get(variableName)

  object VCKickBotCommandConstants {
    val generalCategory = CmdCategory("!", "general command category")
    val commandSettings = CommandSettings(needMention = false, Set(generalCategory))
  }

  def registerCommands[F[_]: Monad: Streamable](helper: CommandsHelper[F]): Unit = {
    helper.registerCommand(
      category = VCKickBotCommandConstants.generalCategory,
      aliases = Seq("vckickusers"),
      filters = Seq(CmdFilter.InGuild),
      description = Some(CmdDescription("VCKick Users", "Kick specified users from voice channel"))
    )(new VCKickUsersHandler[F])
  }

  def setupBotClient(client: DiscordClient[cats.Id]): Unit = {
    client.onEvent { case APIMessage.Ready(_) => println("Client ready.") }
    registerCommands(client)
  }

  def main(args: Array[String]): Unit = {
    val token = fromEnvironment("BOT_TOKEN")
      .getOrElse(throw new RuntimeException("BOT_TOKEN environment variable is not found"))

    ClientSettings(token, commandSettings = VCKickBotCommandConstants.commandSettings)
      .build()
      .applyForeach(setupBotClient)
      .flatMap(_.login())
      .onComplete {
        case scala.util.Success(_) => println("Bot setup completed.")
        case scala.util.Failure(exception) => println("exception caught: "); exception.printStackTrace()
      }
  }

}
