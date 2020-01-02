package sudoku;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.util.HashMap;
import java.util.Map;

/**
 * Table agent, who moderates negotiations between 3 players for choosing the best number in the Sudoku cell.
 * A child of the Teacher agent.
 */
public class Table extends AbstractBehavior<Table.Protocol>
{
	/** Protocol interface for input messages. */
	public interface Protocol {}

	/** Message for creating the Table. */
	public static class CreateMsg implements Protocol
	{
		final int _TableId;
		public CreateMsg(int TableId)
		{
			this._TableId = TableId;
		}
	}

	/** Message for registering a Player. */
	public static class RegisterPlayerMsg implements Protocol
	{
		final ActorRef<Player.Protocol> _playerToRegister;
		final int _playerId;
		final ActorRef<Table.RegisteredMsg> _replyTo;
		public RegisterPlayerMsg(ActorRef<Player.Protocol> playerToRegister, int playerId, ActorRef<Table.RegisteredMsg> replyTo)
		{
			this._playerToRegister = playerToRegister;
			this._playerId = playerId;
			this._replyTo = replyTo;
		}
	}

	/** Message sent out after registering a Player. */
	public static class RegisteredMsg implements Protocol
	{
		final int _playerId;
		final boolean _isItDone;
		public RegisteredMsg(int playerId, boolean isItDone)
		{
			this._playerId = playerId;
			this._isItDone = isItDone;
		}
	}

	/** Custom exception thrown when 4th Player is about to be registered to this table */
	public static class IncorrectRegisterException extends RuntimeException
	{
		public IncorrectRegisterException(String msg)
		{
			super(msg);
		}
	}

	private final int _TableId;
	/** Data structure for storing Players - agents registered to this Table. */
	private Map<Integer, ActorRef<Player.Protocol>> _players;

	/**
	 * Public method that calls private constructor.
	 * Existence required by Akka.
	 * @param createMsg 	message initialising the start of the agent
	 * @return 				wrapped Behavior
	 */
	public static Behavior<Protocol> create(CreateMsg createMsg)
	{
		return Behaviors.setup(context -> new Table(context, createMsg));
	}

	private Table(ActorContext<Protocol> context, CreateMsg createMsg)
	{
		super(context);
		_TableId = createMsg._TableId;
		_players = new HashMap<>();
		// context.getLog().info("Table {} created", _TableId);			// left for debugging only
	}

	/**
	 * Main method controlling incoming messages.
	 * Existence required by Akka.
	 * @return	wrapped Behavior
	 */
	@Override
	public Receive<Protocol> createReceive()
	{
		return newReceiveBuilder()
				.onMessage(RegisterPlayerMsg.class, this::onRegisterPlayer)
				.build();
	}

	/**
	 * Registers new Player to this Table.
	 * When a playerId is already registered, it is replaced with the new ActorRef.
	 * When a 4th Player is about to be registered, IncorrectRegisterException is thrown.
	 * @param msg	message for registering new Player
	 * @return 		wrapped Behavior
	 */
	private Behavior<Protocol> onRegisterPlayer(RegisterPlayerMsg msg)
	{
		int playerId = msg._playerId;

		if(_players.containsKey(playerId))
		{
			_players.remove(playerId);
		}
		else if(_players.size() >= 3)
		{
			msg._replyTo.tell(new RegisteredMsg(playerId, false));
			throw new IncorrectRegisterException("4th Player cannot be registered");
		}

		_players.put(playerId, msg._playerToRegister);
		msg._replyTo.tell(new RegisteredMsg(playerId, true));

		return this;
	}
}
