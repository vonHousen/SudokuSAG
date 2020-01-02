package sudoku;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.util.HashMap;
import java.util.Map;

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

	/** Message for registering a Table. */
	public static class RegisterTableMsg implements Protocol
	{
		final ActorRef<Table.Protocol> _tableToRegister;
		final int _tableId;
		final ActorRef<RegisteredMsg> _replyTo;
		public RegisterTableMsg(ActorRef<Table.Protocol> tableToRegister, int tableId, ActorRef<RegisteredMsg> replyTo)
		{
			this._tableToRegister = tableToRegister;
			this._tableId = tableId;
			this._replyTo = replyTo;
		}
	}

	/** Message sent out after registering a Table. */
	public static class RegisteredMsg implements Protocol
	{
		final int _tableId;
		final boolean _isItDone;
		public RegisteredMsg(int tableId, boolean isItDone)
		{
			this._tableId = tableId;
			this._isItDone = isItDone;
		}
	}

	/** Custom exception thrown when excessive Table is about to be registered to this Player */
	public static class IncorrectRegisterException extends RuntimeException
	{
		public IncorrectRegisterException(String msg)
		{
			super(msg);
		}
	}

	/** Global ID of this Player */
	private final int _playerId;
	/** Data structure for storing Tables - agents registered to this Player. */
	private Map<Integer, ActorRef<Table.Protocol>> _tables;


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
		_tables = new HashMap<>();
		// context.getLog().info("Player {} created", _tableId);		// left for debugging only
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
				.onMessage(RegisterTableMsg.class, this::onRegisterTable)
				.onSignal(PostStop.class, signal -> onPostStop())
				.build();
	}

	/**
	 * Registers new Table to this Player.
	 * When a tableId is already registered, it is replaced with the new ActorRef.
	 * When a excessive Table is about to be registered, IncorrectRegisterException is thrown.
	 * @param msg	message for registering new Table
	 * @return 		wrapped Behavior
	 */
	private Behavior<Protocol> onRegisterTable(RegisterTableMsg msg)
	{
		int tableId = msg._tableId;

		if(_tables.containsKey(tableId))
		{
			_tables.remove(tableId);
		}
		else if(_tables.size() >= this.getExpectedTablesCount())
		{
			msg._replyTo.tell(new RegisteredMsg(tableId, false));
			throw new IncorrectRegisterException("Excessive Table cannot be registered");
		}

		_tables.put(tableId, msg._tableToRegister);
		msg._replyTo.tell(new RegisteredMsg(tableId, true));

		return this;
	}

	/**
	 * Handler of PostStop signal.
	 * Expected after stopping Player agent.
	 * @return 		wrapped Behavior
	 */
	private Player onPostStop()
	{
		// getContext().getLog().info("Player {} stopped", _tableId); 	// left for debugging only
		return this;
	}

	private int getExpectedTablesCount()
	{
		return 9;	// TODO ! Replace with dynamic value
	}
}
