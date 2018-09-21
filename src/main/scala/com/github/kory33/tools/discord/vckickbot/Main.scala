package com.github.kory33.tools.discord.vckickbot

import akka.NotUsed
import cats.{Id, Monad}
import com.github.kory33.tools.discord.util.RichFuture._
import net.katsstuff.ackcord._
import net.katsstuff.ackcord.commands._
import net.katsstuff.ackcord.data.raw.RawChannel
import net.katsstuff.ackcord.data.{ChannelType, Guild, GuildMember}
import net.katsstuff.ackcord.http.rest._
import net.katsstuff.ackcord.util.Streamable

import scala.concurrent.ExecutionContext.Implicits.global

object VCKickUsersHandler {

  private object Constants {
    val intermediaryVCData = CreateGuildChannelData("vckickbot-intermediary", JsonSome(ChannelType.GuildVoice))
  }

  def apply[F[_]: Monad: Streamable](client: DiscordClient[F]): ParsedCmd[F, List[String]] => SourceRequest[Unit] = {
    import client.sourceRequesterRunner._

    client.withCache[SourceRequest, ParsedCmd[F, List[String]]] { implicit c => command =>
      val message = command.msg

      // bot itself might be included, but does not matter since the bot does not join vc
      val targetUserIds = message.mentions.toSet

      import cats.instances.list._
      import cats.syntax.traverse._

      def moveMember(guild: Guild, intermediaryVC: RawChannel)(member: GuildMember): SourceRequest[NotUsed] = {
        val modificationTargetData = ModifyGuildMemberData(channelId = JsonSome(intermediaryVC.id))
        run(ModifyGuildMember(guild.id, member.userId, modificationTargetData))
      }

      for {
        guildChannel <- liftOptionT(message.tGuildChannel[F])
        guild <- liftOptionT(guildChannel.guild)
        intermediaryVC <- run(CreateGuildChannel(guild.id, Constants.intermediaryVCData))

        memberCollectionRequests = for {
          member <- guild.members.values.toList if targetUserIds.contains(member.userId)
        } yield moveMember(guild, intermediaryVC)(member)

        _ <- memberCollectionRequests.sequence[SourceRequest, NotUsed]
        _ <- run(DeleteCloseChannel(intermediaryVC.id))
      } yield ()
    }
  }

}

object Main {

  def fromEnvironment(variableName: String): Option[String] = sys.env.get(variableName)

  object VCKickBotCommandConstants {
    val generalCategory = CmdCategory("!", "general command category")
    val commandSettings: CommandSettings[Id] = CommandSettings[Id](needsMention = false, Set("!"))
  }

  def registerCommands[F[_]: Monad: Streamable](client: DiscordClient[F]): Unit = {
    implicit val parser: MessageParser[List[String]] = MessageParser.fromString(_.split(" ").toList)

    client.registerCmd[List[String], SourceRequest](
      prefix = "!",
      aliases = Seq("vckickusers"),
      filters = Seq(CmdFilter.InGuild),
    )(VCKickUsersHandler[F](client))
  }

  def main(args: Array[String]): Unit = {
    val token = fromEnvironment("BOT_TOKEN")
      .getOrElse(throw new RuntimeException("BOT_TOKEN environment variable is not found"))

    ClientSettings(token, commandSettings = VCKickBotCommandConstants.commandSettings)
      .createClient()
      .applyForeach(registerCommands[Id])
      .flatMap(_.login())
  }

}
