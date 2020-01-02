package sudoku;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

/**
 * Agent that interprets development of the playing agents and rewards them. Singleton.
 * Parent of both Players and Tables.
 */
public class Teacher extends AbstractBehavior<Teacher.Protocol>
{
	public interface Protocol {}

	/**
	 * Message for starting the Teacher.
	 */
	public static class StartMsg implements Protocol
	{
		final String name;

		public StartMsg(String name)
		{
			this.name = name;
		}
	}

	private final Sudoku sudoku;

	public static Behavior<Protocol> create(Sudoku sudoku)
	{
		return Behaviors.setup(context -> new Teacher(context, sudoku));
	}

	private Teacher(ActorContext<Protocol> context, Sudoku sudoku)
	{
		super(context);
		this.sudoku = sudoku;
		context.getLog().info("Teacher started");
	}

	@Override
	public Receive<Protocol> createReceive()
	{
		return newReceiveBuilder()
				.onMessage(StartMsg.class, this::onStart)
				.build();
	}

	private Behavior<Protocol> onStart(StartMsg command)
	{
		return this;
	}

}
