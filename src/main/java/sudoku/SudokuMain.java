package sudoku;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

/**
 * Simple agent-guardian of the whole multi-agent-system.
 * Existence required by Akka.
 */
public class SudokuMain extends AbstractBehavior<SudokuMain.Start>
{
	public static class Start
	{
		public final String name;

		public Start(String name)
		{
			this.name = name;
		}
	}

	public static Behavior<SudokuMain.Start> create()
	{
		return Behaviors.setup(SudokuMain::new);
	}

	private SudokuMain(ActorContext<SudokuMain.Start> context)
	{
		super(context);
		context.getLog().info("SudokuMain started");
	}

	@Override
	public Receive<SudokuMain.Start> createReceive()
	{
		return newReceiveBuilder()
				.onMessage(SudokuMain.Start.class, this::onStart)
				.build();
	}

	private Behavior<SudokuMain.Start> onStart(SudokuMain.Start command)
	{
		return this;
	}
}
