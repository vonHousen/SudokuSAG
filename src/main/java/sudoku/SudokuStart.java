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
		final ActorSystem<SudokuGuardian.Start> greeterMain = ActorSystem.create(SudokuGuardian.create(), "StartSudoku");

		try
		{
			System.out.println(">>> Press ENTER to exit <<<");
			System.in.read();
		}
		catch (IOException ignored)
		{
		}
		finally
		{
			greeterMain.terminate();
		}
	}
}