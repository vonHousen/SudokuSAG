package sudoku;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;


/** Simple reactive agent that replies after some time. */
public class TimerManager extends AbstractBehavior<TimerManager.Protocol>
{
	/** Protocol interface for input messages. */
	public interface Protocol {}

	/** Message creating the agent. */
	public static class CreateMsg implements Protocol
	{
		public final ActorRef<Teacher.Protocol> _parent;
		public CreateMsg(ActorRef<Teacher.Protocol> parent)
		{
			this._parent = parent;
		}
	}

	/** Message from the Teacher waking him up after passing given time to check if its Tables are still alive. */
	public static class RemindToCheckTablesMsg implements Protocol, SharedProtocols.ValidationProtocol
	{
		public final int _waitMilliseconds;
		public final int[] _tableIds;
		public RemindToCheckTablesMsg(int waitMilliseconds, int[] tableIds)
		{
			this._waitMilliseconds = waitMilliseconds;
			this._tableIds = tableIds;
		}
	}


	/** Parent - the only agent TimeManager replies to. */
	private final ActorRef<Teacher.Protocol> _parent;

	/** Table Ids requested to be checked on the last message. */
	private int[] _latelyRequestedTableIds;		// TODO Kamil

	/** Private constructor called only by CreateMsg. */
	private TimerManager(ActorContext<TimerManager.Protocol> context, CreateMsg msg)
	{
		super(context);
		this._parent = msg._parent;
	}

	/**
	 * Public method that calls private constructor.
	 * Existence required by Akka.
	 * @return wrapped Behavior
	 */
	public static Behavior<TimerManager.Protocol> create(CreateMsg msg)
	{
		return Behaviors.setup(context -> new TimerManager(context, msg));
	}

	/**
	 * Main method controlling incoming messages.
	 * Existence required by Akka.
	 * @return 		wrapped Behavior
	 */
	@Override
	public Receive<TimerManager.Protocol> createReceive()
	{
		return newReceiveBuilder()
				.onMessage(RemindToCheckTablesMsg.class, this::onRemindToCheckTables)
				.build();
	}


	/**
	 * Timer reminds the Teacher of checking if its Tables are still alive.
	 * @param msg	request from the Teacher
	 * @return 		wrapped Behavior
	 */
	private Behavior<TimerManager.Protocol> onRemindToCheckTables(RemindToCheckTablesMsg msg)
	{
		try
		{
			Thread.sleep(msg._waitMilliseconds);
		}
		catch (Exception e)
		{
			getContext().getLog().info("Exception thrown while sleeping.");
		}

		this._parent.tell(new Teacher.CheckTblMsg(msg._tableIds));
		return this;
	}
}