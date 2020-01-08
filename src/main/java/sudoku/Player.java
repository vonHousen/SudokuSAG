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

	/** Protocol interface for messages for initialisation strategy. */
	public interface InitialisationProtocol extends Protocol {}

	/** Protocol interface for messages during negotiations. */
	public interface NegotiationsProtocol extends Protocol {}

	/** Message for creating the Player. */
	public static class CreateMsg implements InitialisationProtocol
	{
		final int _playerId;
		final Vector2d _playerPosition;
		final Type _playerType;
		final int[] _digitVector;
		final boolean[] _digitMask;

		public CreateMsg(int playerId, Vector2d playerPosition, Type playerType, int[] digitVector, boolean[] digitMask)
		{
			this._playerId = playerId;
			this._playerPosition = playerPosition;
			this._playerType = playerType;
			this._digitVector = digitVector;
			this._digitMask = digitMask;
		}
	}

	/** Message for registering a Table. */
	public static class RegisterTableMsg implements InitialisationProtocol
	{
		final ActorRef<Table.Protocol> _tableToRegister;
		final Vector2d _tablePos;
		final ActorRef<RegisteredMsg> _replyTo;
		public RegisterTableMsg(ActorRef<Table.Protocol> tableToRegister, Vector2d tablePos, ActorRef<RegisteredMsg> replyTo)
		{
			this._tableToRegister = tableToRegister;
			this._tablePos = tablePos;
			this._replyTo = replyTo;
		}
	}

	/** Message sent out after registering a Table. */
	public static class RegisteredMsg implements InitialisationProtocol
	{
		final Vector2d _tablePos;
		final boolean _isItDone;
		public RegisteredMsg(Vector2d tablePos, boolean isItDone)
		{
			this._tablePos = tablePos;
			this._isItDone = isItDone;
		}
	}

	/** Abstract class for messages received from Table Agent during negotiations. */
	public static abstract class NegotiationsMsg implements NegotiationsProtocol
	{
		private final ActorRef<Table.Protocol> _replyTo;
		private final int _tableId;

		protected NegotiationsMsg(ActorRef<Table.Protocol> replyTo, int tableId)
		{
			this._replyTo = replyTo;
			this._tableId = tableId;
		}
	}

	/** Message received from the Table, requesting for additional info about other stakeholder's digit. */
	public static class AdditionalInfoRequestMsg extends NegotiationsMsg
	{
		public final int _otherDigit;

		public AdditionalInfoRequestMsg(int otherDigit, ActorRef<Table.Protocol> replyTo, int tableId)
		{
			super(replyTo, tableId);
			this._otherDigit = otherDigit;
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

	/** Defines if Player is a Row, a Column or a square Block */
	public enum Type
	{
		COLUMN,
		ROW,
		BLOCK
	}

	/** Global ID of this Player */
	private final int _playerId;
	/** Data structure for storing Tables (agents registered to this Player) and internal sudoku digit indices */
	private final Map<Vector2d, Pair<ActorRef<Table.Protocol>, Integer>> _tableIndex;
	/** Structure containing awards and current digit vector */
	private final Memory _memory;

	/** Add all keys to _tableIndex with correct index and actor reference set to null. */
	private void fillTableIndex(Vector2d origin, Type t, boolean[] digitMask)
	{
		switch (t)
		{
			case COLUMN:
				for (int i = 0; i < digitMask.length; ++i)
				{
					if (!digitMask[i])
					{
						_tableIndex.put(new Vector2d(origin.x, i), new Pair<ActorRef<Table.Protocol>, Integer>(null, i));
					}
				}
				break;
			case ROW:
				for (int i = 0; i < digitMask.length; ++i)
				{
					if (!digitMask[i])
					{
						_tableIndex.put(new Vector2d(i, origin.y), new Pair<ActorRef<Table.Protocol>, Integer>(null, i));
					}
				}
				break;
			case BLOCK:
				for (int i = 0; i < digitMask.length; ++i)
				{
					if (!digitMask[i])
					{
						_tableIndex.put(new Vector2d(origin.x + (i % digitMask.length), origin.y + (i / digitMask.length)), new Pair<ActorRef<Table.Protocol>, Integer>(null, i));
					}
				}
		}
	}

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
		_tableIndex = new HashMap<>();
		fillTableIndex(createMsg._playerPosition, createMsg._playerType, createMsg._digitMask);
		_memory = new Memory(createMsg._digitVector, createMsg._digitMask);
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
	 * It is expected that a table position is already registered and it is replaced with the new ActorRef.
	 * When a excessive Table is about to be registered, IncorrectRegisterException is thrown.
	 * @param msg	message for registering new Table
	 * @return 		wrapped Behavior
	 */
	private Behavior<Protocol> onRegisterTable(RegisterTableMsg msg)
	{
		Integer index;
		if (_tableIndex.containsKey(msg._tablePos))
		{
			index = _tableIndex.get(msg._tablePos).second;
			_tableIndex.remove(msg._tablePos);
		}
		else
		{
			msg._replyTo.tell(new RegisteredMsg(msg._tablePos, false));
			throw new IncorrectRegisterException("Excessive Table cannot be registered");
		}

		_tableIndex.put(msg._tablePos, new Pair<ActorRef<Table.Protocol>, Integer>(msg._tableToRegister, index));
		msg._replyTo.tell(new RegisteredMsg(msg._tablePos, true));

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
}
