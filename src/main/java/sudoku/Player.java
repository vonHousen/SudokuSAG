package sudoku;

import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

/**
 * Playing agent, who actually learns to solve Sudoku.
 * A child of the Teacher agent.
 */
public class Player extends AbstractBehavior<Player.Protocol>
{
	/** Protocol interface for input messages. */
	public interface Protocol {}

	/** Message for creating the Player. */
	public static class CreateMsg implements Protocol
	{
		final int _playerId;

		public CreateMsg(int playerId)
		{
			this._playerId = playerId;
		}
	}

	private final int _playerId;

	/**
	 * Public method that calls private constructor.
	 * Existence required by Akka.
	 * @param createMsg 	message initialising the start of the agent
	 * @return 		wrapped Behavior
	 */
	public static Behavior<Player.Protocol> create(CreateMsg createMsg)
	{
		return Behaviors.setup(context -> new Player(context, createMsg));
	}

	private Player(ActorContext<Protocol> context, CreateMsg createMsg)
	{
		super(context);
		_playerId = createMsg._playerId;
		// context.getLog().info("Player {} created", _playerId);		// left for debugging only
	}

	/**
	 * Main method controlling incoming messages.
	 * Existence required by Akka.
	 * @return 		wrapped Behavior
	 */
	@Override
	public Receive<Protocol> createReceive()
	{
		return newReceiveBuilder()
				.onSignal(PostStop.class, signal -> onPostStop())
				.build();
	}

	/**
	 * Handler of PostStop signal.
	 * Expected after stopping Player agent.
	 * @return 		wrapped Behavior
	 */
	private Player onPostStop()
	{
		// getContext().getLog().info("Player {} stopped", _playerId); 	// left for debugging only
		return this;
	}

}
