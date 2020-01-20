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

	private Sudoku createSudokuFromNaturalBoard(int rank, int[][] naturalSudokuBoard)
	{
		Sudoku sudoku = new Sudoku(rank);
		int[][] transformedSudokuBoard = new int[rank*rank][rank*rank];
		for(int x = 0; x < rank*rank; ++x)
			for(int y = 0; y < rank*rank; ++y)
				transformedSudokuBoard[x][y] = naturalSudokuBoard[y][x];
		sudoku.setBoard(transformedSudokuBoard);
		return sudoku;
	}

	@Test
	public void testSolvingSimplestSudoku()
	{
		int NO = 3;
		int rank = 2;
		int[][] naturalBoard = {
				{1,2,3,4},
				{3,4,1,2},
				{2,1,4,3},
				{4,3,2,0}
		};
		int[][] naturalSolution = {
				{1,2,3,4},
				{3,4,1,2},
				{2,1,4,3},
				{4,3,2,1}
		};
		Sudoku sudoku = createSudokuFromNaturalBoard(rank, naturalBoard);
		Sudoku sudokuSolution = createSudokuFromNaturalBoard(rank, naturalSolution);

		// create the Supervisor & Teacher
		TestProbe<SudokuSupervisor.Protocol> dummyGuardian = testKit.createTestProbe();
		ActorRef<Teacher.Protocol> theTeacher = testKit.spawn(Teacher.create(
				new Teacher.CreateMsg("teacher-" + NO, sudoku, dummyGuardian.getRef())
		), "test-" + NO);

		SudokuSupervisor.IterationFinishedMsg firstIterationResults =
				(SudokuSupervisor.IterationFinishedMsg) dummyGuardian.receiveMessage();
		Sudoku firstIterationSudoku = firstIterationResults._newSolution;

		// see how good are first iteration's results
		sudoku.printNatural();
		System.out.println();
		firstIterationSudoku.printNatural();

		assertNotEquals(sudoku, sudokuSolution);				// just check if Teacher's constructor uses deep copy
		assertEquals(firstIterationSudoku, sudokuSolution);		// check if solved
	}

	@Test
	public void testSolvingSudoku_1()
	{
		int NO = 4;
		int rank = 2;
		int[][] naturalBoard = {
				{0,2,3,4},
				{3,0,1,2},
				{2,1,0,3},
				{4,3,2,0}
		};
		int[][] naturalSolution = {
				{1,2,3,4},
				{3,4,1,2},
				{2,1,4,3},
				{4,3,2,1}
		};
		Sudoku sudoku = createSudokuFromNaturalBoard(rank, naturalBoard);
		Sudoku sudokuSolution = createSudokuFromNaturalBoard(rank, naturalSolution);

		// create the Supervisor & Teacher
		TestProbe<SudokuSupervisor.Protocol> dummyGuardian = testKit.createTestProbe();
		ActorRef<Teacher.Protocol> theTeacher = testKit.spawn(Teacher.create(
				new Teacher.CreateMsg("teacher-" + NO, sudoku, dummyGuardian.getRef())
		), "test-" + NO);

		SudokuSupervisor.IterationFinishedMsg results =
				(SudokuSupervisor.IterationFinishedMsg) dummyGuardian.receiveMessage();
		Sudoku sudokuResults = results._newSolution;

		// see how good are first iteration's results
		sudoku.printNatural();
		System.out.println();
		sudokuResults.printNatural();

		if(!sudokuResults.equals(sudokuSolution))		// if not equals, give another chance
		{
			results = (SudokuSupervisor.IterationFinishedMsg) dummyGuardian.receiveMessage();
			sudokuResults = results._newSolution;
			System.out.println();
			sudokuResults.printNatural();
		}

		assertEquals(sudokuSolution, sudokuResults);
	}
}
