package sudoku;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;


/** Simple reactive agent that replies after some time. */
public class Timer extends AbstractBehavior<Timer.Protocol>
{
	/** Protocol interface for input messages. */
	public interface Protocol {}

	/** Message creating the agent. */
	public static class CreateMsg implements Protocol {}

	/** Message from the Teacher waking him up after passing given time to check if its Tables are still alive. */
	public static class RemindToCheckTablesMsg implements Protocol, SharedProtocols.ValidationProtocol
	{
		public final ActorRef<Teacher.Protocol> _replyTo;
		public final int _waitMilliseconds;
		public final int[] _tableIds;
		public RemindToCheckTablesMsg(ActorRef<Teacher.Protocol> replyTo, int waitMilliseconds, int[] tableIds)
		{
			this._replyTo = replyTo;
			this._waitMilliseconds = waitMilliseconds;
			this._tableIds = tableIds;
		}
	}


	/** Private constructor called only by CreateMsg. */
	private Timer(ActorContext<Timer.Protocol> context)
	{
		super(context);
	}

	/**
	 * Public method that calls private constructor.
	 * Existence required by Akka.
	 * @return wrapped Behavior
	 */
	public static Behavior<Timer.Protocol> create()
	{
		return Behaviors.setup(Timer::new);
	}

	/**
	 * Main method controlling incoming messages.
	 * Existence required by Akka.
	 * @return 		wrapped Behavior
	 */
	@Override
	public Receive<Timer.Protocol> createReceive()
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
	private Behavior<Timer.Protocol> onRemindToCheckTables(RemindToCheckTablesMsg msg)
	{
		try
		{
			Thread.sleep(msg._waitMilliseconds);
		}
		catch (Exception e)
		{
			getContext().getLog().info("Exception thrown while sleeping.");
		}

		msg._replyTo.tell(new Teacher.CheckTblMsg(msg._tableIds));
		return this;
	}
}