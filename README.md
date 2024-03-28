# stereotypez-mattermost

Akka-typed behaviors for the Mattermost API.

## Example (using akka-typed)

```scala
import de.ship.mattermost.ws.BotActor
import de.ship.mattermost.ws.Protocol._
import de.ship.mattermost.ws.events._
import akka.actor.typed.ActorSystem

object SomeProject {
  val system = ActorSystem[Nothing](
    Behaviors.setup { ctx =>
      val logger = ctx.spawnAnonymous(Behaviors.setup { ctx =>
        // ws ! RegisterHook(ctx.self) // or hook your self
        Behaviors.receiveMessage { msg =>
          ctx.log.info(s"received $msg")

        }
      })
      val ws = ctx.spawnAnonymous(
        BotActor.apply(
          websocketUrl = " ... ",
          botToken = " ... ",
          initialHooks = Seq(logger)
        )
      )

      Behaviors.ignore
    },
    "some-system"
  )
}
```
