package sudoku;

import akka.actor.typed.ActorSystem;
import java.io.IOException;

/**
 * Starting point of the whole application.
 */
public class SudokuStart
{
	public static void main(String[] args)
	{
		final ActorSystem<SudokuSupervisor.Command> sudokuGuardian
				= ActorSystem.create(SudokuSupervisor.create(), "startSudoku");
		try
		{
			System.out.println(">>> Press ENTER to stop SudokuSupervisor <<<");
			System.in.read();
			sudokuGuardian.tell(new SudokuSupervisor.TerminateMsg(0L, null));
			System.out.println(">>> Press ENTER once again to exit <<<");
			System.in.read();
		}
		catch (IOException ignored)
		{
		}
		finally
		{
			sudokuGuardian.terminate();
		}
	}
}