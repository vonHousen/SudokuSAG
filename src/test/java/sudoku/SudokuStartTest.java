package sudoku;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.*;
import org.junit.ClassRule;
import org.junit.Test;

import java.time.Duration;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SudokuStartTest
{
	@ClassRule
	public static final TestKitJunitResource testKit = new TestKitJunitResource();

	@Test
	public void test_1_SimpleStart()
	{
		TestProbe<Void> testProbe = testKit.createTestProbe();
		ActorRef<SudokuSupervisor.Protocol> guardian = testKit.spawn(SudokuSupervisor.create(),"test1");
		testProbe.expectNoMessage();
	}

	@Test
	public void test_2_SupervisorTermination()
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
	public void test_3_SolvingSimplestSudoku()
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
	public void test_4_SolvingSudoku()
	{
		int NO = 4;
		int chances = 1;
		int rank = 2;
		int[][] naturalBoard = {
				{0,2,3,4},
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

		SudokuSupervisor.IterationFinishedMsg results =
				(SudokuSupervisor.IterationFinishedMsg) dummyGuardian.receiveMessage();
		Sudoku sudokuResults = results._newSolution;

		// see how good are first iteration's results
		sudoku.printNatural();
		System.out.println();
		sudokuResults.printNatural();

		while(!sudokuResults.equals(sudokuSolution) && chances-- > 0)		// give another chance
		{
			results = (SudokuSupervisor.IterationFinishedMsg) dummyGuardian.receiveMessage();
			sudokuResults = results._newSolution;
			System.out.println();
			sudokuResults.printNatural();
		}

		assertEquals(sudokuSolution, sudokuResults);
	}

	@Test
	public void test_5_SolvingSudoku()
	{
		int NO = 5;
		int chances = 2;
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

		sudoku.printNatural();
		System.out.println();
		SudokuSupervisor.IterationFinishedMsg results =
				(SudokuSupervisor.IterationFinishedMsg) dummyGuardian.receiveMessage();
		Sudoku sudokuResults = results._newSolution;

		// see how good are first iteration's results
		sudokuResults.printNatural();

		while(!sudokuResults.equals(sudokuSolution) && chances-- > 0)		// give another chance
		{
			results = (SudokuSupervisor.IterationFinishedMsg) dummyGuardian.receiveMessage();
			sudokuResults = results._newSolution;
			System.out.println();
			sudokuResults.printNatural();
		}

		assertEquals(sudokuSolution, sudokuResults);
	}

	@Test
	public void test_6_SolvingSudoku()
	{
		int NO = 6;
		int chances = 0;
		int rank = 2;
		int[][] naturalBoard = {
				{0,2,3,0},
				{3,4,1,0},
				{2,1,4,0},
				{0,0,0,1}
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

		sudoku.printNatural();
		System.out.println();
		SudokuSupervisor.IterationFinishedMsg results =
				(SudokuSupervisor.IterationFinishedMsg) dummyGuardian.receiveMessage();
		Sudoku sudokuResults = results._newSolution;

		// see how good are first iteration's results
		sudokuResults.printNatural();

		while(!sudokuResults.equals(sudokuSolution) && chances-- > 0)		// give another chance
		{
			results = (SudokuSupervisor.IterationFinishedMsg) dummyGuardian.receiveMessage();
			sudokuResults = results._newSolution;
			System.out.println();
			sudokuResults.printNatural();
		}

		assertEquals(sudokuSolution, sudokuResults);
	}

	@Test
	public void test_7_SolvingSudoku()
	{
		int NO = 7;
		int chances = 0;
		int rank = 3;
		int[][] naturalBoard = {
				{0,4,6,0,5,7,9,1,0},
				{1,8,9,6,4,3,2,7,0},
				{5,7,3,2,9,1,4,8,0},
				{0,1,8,0,2,9,5,6,0},
				{6,3,7,4,8,5,1,2,0},
				{9,5,2,1,7,6,3,4,0},
				{7,6,4,5,3,2,8,9,0},
				{3,2,1,9,6,8,7,5,0},
				{0,0,0,0,0,0,0,0,2}
		};
		int[][] naturalSolution = {
				{2,4,6,8,5,7,9,1,3},
				{1,8,9,6,4,3,2,7,5},
				{5,7,3,2,9,1,4,8,6},
				{4,1,8,3,2,9,5,6,7},
				{6,3,7,4,8,5,1,2,9},
				{9,5,2,1,7,6,3,4,8},
				{7,6,4,5,3,2,8,9,1},
				{3,2,1,9,6,8,7,5,4},
				{8,9,5,7,1,4,6,3,2}
		};
		Sudoku sudoku = createSudokuFromNaturalBoard(rank, naturalBoard);
		Sudoku sudokuSolution = createSudokuFromNaturalBoard(rank, naturalSolution);

		// create the Supervisor & Teacher
		TestProbe<SudokuSupervisor.Protocol> dummyGuardian = testKit.createTestProbe();
		ActorRef<Teacher.Protocol> theTeacher = testKit.spawn(Teacher.create(
				new Teacher.CreateMsg("teacher-" + NO, sudoku, dummyGuardian.getRef())
		), "test-" + NO);

		SudokuSupervisor.IterationFinishedMsg results =
				(SudokuSupervisor.IterationFinishedMsg) dummyGuardian.receiveMessage(Duration.ofSeconds(10));
		Sudoku sudokuResults = results._newSolution;

		// see how good are first iteration's results
		sudoku.printNatural();
		System.out.println();
		sudokuResults.printNatural();

		while(!sudokuResults.equals(sudokuSolution) && chances-- > 0)		// give another chance
		{
			results = (SudokuSupervisor.IterationFinishedMsg) dummyGuardian.receiveMessage();
			sudokuResults = results._newSolution;
			System.out.println();
			sudokuResults.printNatural();
		}

		assertEquals(sudokuSolution, sudokuResults);
	}

	@Test
	public void test_8_SolvingSudoku()
	{
		int NO = 8;
		int chances = 0;
		int rank = 3;
		int[][] naturalBoard = {
				{2,4,6,8,5,7,9,1,3},
				{1,8,9,6,4,3,2,7,5},
				{5,7,3,2,9,1,4,8,6},
				{4,1,8,3,2,9,5,6,7},
				{6,3,7,4,8,5,1,2,9},
				{9,5,2,1,7,6,3,4,8},
				{7,6,4,5,3,2,8,9,0},
				{3,2,1,9,6,8,7,5,4},
				{8,9,5,7,0,4,6,0,2}
		};
		int[][] naturalSolution = {
				{2,4,6,8,5,7,9,1,3},
				{1,8,9,6,4,3,2,7,5},
				{5,7,3,2,9,1,4,8,6},
				{4,1,8,3,2,9,5,6,7},
				{6,3,7,4,8,5,1,2,9},
				{9,5,2,1,7,6,3,4,8},
				{7,6,4,5,3,2,8,9,1},
				{3,2,1,9,6,8,7,5,4},
				{8,9,5,7,1,4,6,3,2}
		};
		Sudoku sudoku = createSudokuFromNaturalBoard(rank, naturalBoard);
		Sudoku sudokuSolution = createSudokuFromNaturalBoard(rank, naturalSolution);

		// create the Supervisor & Teacher
		TestProbe<SudokuSupervisor.Protocol> dummyGuardian = testKit.createTestProbe();
		ActorRef<Teacher.Protocol> theTeacher = testKit.spawn(Teacher.create(
				new Teacher.CreateMsg("teacher-" + NO, sudoku, dummyGuardian.getRef())
		), "test-" + NO);

		SudokuSupervisor.IterationFinishedMsg results =
				(SudokuSupervisor.IterationFinishedMsg) dummyGuardian.receiveMessage(Duration.ofSeconds(10));
		Sudoku sudokuResults = results._newSolution;

		// see how good are first iteration's results
		sudoku.printNatural();
		System.out.println();
		sudokuResults.printNatural();

		while(!sudokuResults.equals(sudokuSolution) && chances-- > 0)		// give another chance
		{
			results = (SudokuSupervisor.IterationFinishedMsg) dummyGuardian.receiveMessage();
			sudokuResults = results._newSolution;
			System.out.println();
			sudokuResults.printNatural();
		}

		assertEquals(sudokuSolution, sudokuResults);
	}

	@Test
	public void test_9_SolvingSudoku()
	{
		int NO = 9;
		int chances = 0;
		int rank = 3;
		int[][] naturalBoard = {
				{0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0}
		};
		int[][] naturalSolution = {
				{0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0}
		};
		Sudoku sudoku = createSudokuFromNaturalBoard(rank, naturalBoard);
		Sudoku sudokuSolution = createSudokuFromNaturalBoard(rank, naturalSolution);

		// create the Supervisor & Teacher
		TestProbe<SudokuSupervisor.Protocol> dummyGuardian = testKit.createTestProbe();
		ActorRef<Teacher.Protocol> theTeacher = testKit.spawn(Teacher.create(
				new Teacher.CreateMsg("teacher-" + NO, sudoku, dummyGuardian.getRef())
		), "test-" + NO);

		SudokuSupervisor.IterationFinishedMsg results =
				(SudokuSupervisor.IterationFinishedMsg) dummyGuardian.receiveMessage(Duration.ofSeconds(10));
		Sudoku sudokuResults = results._newSolution;

		// see how good are first iteration's results
		sudoku.printNatural();
		System.out.println();
		sudokuResults.printNatural();

		while(!sudokuResults.equals(sudokuSolution) && chances-- > 0)		// give another chance
		{
			results = (SudokuSupervisor.IterationFinishedMsg) dummyGuardian.receiveMessage();
			sudokuResults = results._newSolution;
			System.out.println();
			sudokuResults.printNatural();
		}

		//assertEquals(sudokuSolution, sudokuResults);
	}
}
