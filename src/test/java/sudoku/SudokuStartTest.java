package sudoku;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.*;
import org.junit.ClassRule;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SudokuStartTest
{
	@ClassRule
	public static final TestKitJunitResource testKit = new TestKitJunitResource();

	@Test
	public void testSimpleStart()
	{
		TestProbe<Void> testProbe = testKit.createTestProbe();
		ActorRef<SudokuSupervisor.Protocol> guardian = testKit.spawn(SudokuSupervisor.create(),"test1");
		testProbe.expectNoMessage();
	}

	@Test
	public void testSupervisorTermination()
	{
		TestProbe<String> testProbe = testKit.createTestProbe();
		ActorRef<SudokuSupervisor.Protocol> guardian = testKit.spawn(SudokuSupervisor.create(),"test2");
		guardian.tell(new SudokuSupervisor.TerminateMsg(0L, testProbe.getRef()));
		testProbe.expectMessage("I will be terminated.");

		// below is just printout control
		try{Thread.sleep(500);} catch (Exception e){}
		System.out.println("\n======================================> Test finished\n");
	}

	@Test
	public void testSolvingSimplestSudoku()
	{
		TestProbe<SudokuSupervisor.Protocol> dummyGuardian = testKit.createTestProbe();

		int rank = 2;
		Sudoku sudoku = new Sudoku(rank);
		Sudoku sudokuSolution = new Sudoku(rank);
		int[][] naturalSudokuBoard = {
				// yOri = 0 -> 3
				// x = 0 ----> 3
				{1,2,3,4},		// xOri = 0, y = 0
				{3,4,1,2},
				{2,1,4,3},
				{4,3,2,0}		// xOri = 3, y = 3
		};
		int[][] naturalSudokuBoardSolution = {
				// yOri = 0 -> 3
				// x = 0 ----> 3
				{1,2,3,4},		// xOri = 0, y = 0
				{3,4,1,2},
				{2,1,4,3},
				{4,3,2,1}		// xOri = 3, y = 3
		};
		int[][] transformedSudokuBoard = new int[rank*rank][rank*rank];
		int[][] transformedSudokuBoardSolution = new int[rank*rank][rank*rank];
		for(int x = 0; x < rank*rank; ++x)
			for(int y = 0; y < rank*rank; ++y)
				transformedSudokuBoard[x][y] = naturalSudokuBoard[y][x];
		sudoku.setBoard(transformedSudokuBoard);
		for(int x = 0; x < rank*rank; ++x)
			for(int y = 0; y < rank*rank; ++y)
				transformedSudokuBoardSolution[x][y] = naturalSudokuBoardSolution[y][x];
		sudokuSolution.setBoard(transformedSudokuBoardSolution);

		ActorRef<Teacher.Protocol> theTeacher = testKit.spawn(Teacher.create(
				new Teacher.CreateMsg("teacher", sudoku, dummyGuardian.getRef())
		), "test3");

		SudokuSupervisor.IterationFinishedMsg firstIterationResults =
				(SudokuSupervisor.IterationFinishedMsg) dummyGuardian.receiveMessage();
		Sudoku firstIterationSudoku = firstIterationResults._newSolution;

		sudoku.printNatural();
		System.out.println();
		firstIterationSudoku.printNatural();

		assertNotEquals(sudoku, sudokuSolution);				// just check if Teacher's constructor uses deep copy
		assertEquals(firstIterationSudoku, sudokuSolution);
	}
}
