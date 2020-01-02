package sudoku;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

/**
 * Simple agent-guardian of the whole multi-agent-system solving Sudoku.
 * Existence required by Akka.
 * Parent of Teacher agent.
 */
public class SudokuSupervisor extends AbstractBehavior<SudokuSupervisor.Command>
{
	public interface Command {}

	/**
	 * Message for controlled termination of the agent.
	 */
	public static final class TerminateMsg implements Command
	{
		final long _requestId;
		final ActorRef<String> _replyTo;

		TerminateMsg(long requestId, ActorRef<String> replyTo)
		{
			this._requestId = requestId;
			this._replyTo = replyTo;
		}
	}

	/** Message for making the Teacher crash. */
	public static class SimulateTeacherCrashMsg implements Command
	{
		final ActorRef<String> _replyTo;
		public SimulateTeacherCrashMsg(ActorRef<String> replyTo)
		{
			this._replyTo = replyTo;
		}
	}

	/** Message when Teacher is going to be restarted. */
	public static class TeacherWillRestartMsg implements Command
	{
		final String _msg;
		public TeacherWillRestartMsg(String msg)
		{
			this._msg = msg;
		}
	}

	/** Sudoku riddle to be solved by the app. */
	private Sudoku _sudoku;
	/** Child Teacher agent. */
	private ActorRef<Teacher.Protocol> _teacher;
	/** Parent - agent for debugging when simulating Teacher's crash. */
	private ActorRef<String> _simulationParent;

	/**
	 * Public method that calls private constructor.
	 * Existence required by Akka.
	 * @return N/A
	 */
	public static Behavior<SudokuSupervisor.Command> create()
	{
		return Behaviors.setup(SudokuSupervisor::new);
	}

	private SudokuSupervisor(ActorContext<SudokuSupervisor.Command> context)
	{
		super(context);
		context.getLog().info("SudokuSupervisor started");
		readSudoku();
		_teacher = context.spawn(
				Behaviors.supervise(
						Teacher.create(new Teacher.CreateMsg("TheOnlyTeacher", _sudoku, context.getSelf()))
				).onFailure(SupervisorStrategy.restart()), "teacher"
		);
		// getContext().watchWith(_teacher, new TerminateMsg(1L, getContext().getSelf())); TODO
	}

	/**
	 * Main method controlling incoming messages.
	 * Existence required by Akka.
	 * @return N/A
	 */
	@Override
	public Receive<SudokuSupervisor.Command> createReceive()
	{
		return newReceiveBuilder()
				.onMessage(TerminateMsg.class, this::onTermination)
				.onMessage(SimulateTeacherCrashMsg.class, this::onSimulateTeacherCrash)
				.onMessage(TeacherWillRestartMsg.class, this::onTeacherWillRestartMsg)
				.onSignal(PostStop.class, signal -> onPostStop())
				.build();
	}

	/**
	 * Handler of PostStop signal.
	 * Expected after stopping SudokuSupervisor agent.
	 * @return N/A
	 */
	private SudokuSupervisor onPostStop()
	{
		getContext().getLog().info("SudokuSupervisor stopped.");
		return this;
	}

	/**
	 * Behaviour towards TerminateMsg message.
	 * Action performed on termination.
	 * @param terminateMsg	termination message
	 * @return N/A
	 */
	private Behavior<SudokuSupervisor.Command> onTermination(TerminateMsg terminateMsg)
	{
		getContext().getLog().info("SudokuSupervisor will be terminated.");
		terminateMsg._replyTo.tell("I will be terminated.");
		return Behaviors.stopped();
	}

	/**
	 * Behaviour towards SimulateTeacherCrashMsg message.
	 * Agent tells the Teacher to simulate crash.
	 * @param msg	 message simulating crash
	 * @return N/A
	 */
	private Behavior<SudokuSupervisor.Command> onSimulateTeacherCrash(SimulateTeacherCrashMsg msg)
	{
		_teacher.tell(new Teacher.SimulateCrashMsg());
		_simulationParent = msg._replyTo;
		return this;
	}

	/**
	 * Behaviour towards TeacherWillRestartMsg message.
	 * The Teacher responds just before restart.
	 * @param msg	 respond just before restart
	 * @return N/A
	 */
	private Behavior<SudokuSupervisor.Command> onTeacherWillRestartMsg(TeacherWillRestartMsg msg)
	{
		_simulationParent.tell(msg._msg);
		return this;
	}

	/**
	 * Action of reading _sudoku from file.
	 */
	private void readSudoku()
	{
		_sudoku = new Sudoku();	// TODO implement reading _sudoku from file
	}
}
