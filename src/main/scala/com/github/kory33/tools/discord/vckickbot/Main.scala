package com.github.kory33.tools.discord.vckickbot

import cats.Monad
import com.github.kory33.tools.discord.util.RichFuture._
import com.github.kory33.tools.discord.util.RichRequestDSL._
import net.katsstuff.ackcord._
import net.katsstuff.ackcord.commands.{CmdCategory, CmdDescription, CmdFilter, ParsedCmd}
import net.katsstuff.ackcord.data.{ChannelType, GuildMember}
import net.katsstuff.ackcord.http.rest._
import net.katsstuff.ackcord.util.Streamable

import scala.concurrent.ExecutionContext.Implicits.global

class VCKickUsersHandler[F[_]: Monad] extends (ParsedCmd[F, List[String]] => Unit) {
  private object Constants {
    val intermediaryVCData = CreateGuildChannelData("vckickbot-intermediary", RestSome(ChannelType.GuildVoice))
  }

  override def apply(command: ParsedCmd[F, List[String]]): Unit = {
    implicit val cache: CacheSnapshot[F] = command.cache

    val message = command.msg

    // bot itself might be included, but does not matter since the bot does not join vc
    val targetUserIds = message.mentions.toSet

    import RequestDSL._
    import cats.instances.list._

    for {
      guildChannel <- message.tGuildChannel[F]
      guild <- guildChannel.guild
    } yield for {
      intermediaryVC <- CreateGuildChannel(guild.id, Constants.intermediaryVCData)

      moveMember = { member: GuildMember =>
        val modificationTargetData = ModifyGuildMemberData(channelId = RestSome(intermediaryVC.id))
        wrap(ModifyGuildMember(guild.id, member.userId, modificationTargetData))
      }

      memberCollectionActions = for {
        member <- guild.members.values.toList if targetUserIds.contains(member.userId)
      } yield moveMember(member)

      _ <- catsStdInstancesForList
        .sequence(memberCollectionActions)
        .andThen(DeleteCloseChannel(intermediaryVC.id))
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
