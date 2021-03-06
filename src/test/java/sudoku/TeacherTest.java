package sudoku;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.*;
import org.junit.ClassRule;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

public class TeacherTest
{
	@ClassRule
	public static final TestKitJunitResource testKit = new TestKitJunitResource();

	@Test
	public void testCrashingTeacher()
	{
		TestProbe<String> testProbe = testKit.createTestProbe();
		ActorRef<SudokuSupervisor.Protocol> guardian = testKit.spawn(SudokuSupervisor.create(),"test3");
		guardian.tell(new SudokuSupervisor.SimulateTeacherCrashMsg(testProbe.getRef()));
		String response = testProbe.receiveMessage();
		assertEquals("I will be restarted.", response);

		// below is just printout control
		try{Thread.sleep(1000);} catch (Exception e){}
		System.out.println("\n======================================> Test finished\n");
	}

	@Test
	public void testPassingSudokuDigits()
	{
		TestProbe<Sudoku> dummyInspector = testKit.createTestProbe();
		TestProbe<SudokuSupervisor.Protocol> dummyGuardian = testKit.createTestProbe();

		int rank = 3;
		Sudoku sudoku = new Sudoku(rank);
		int[][] naturalSudokuBoard = {
		//		yOri = 0 -------> 8
		//		x = 0 ----------> 8
				{0,1,0,3,0,5,0,0,0},	// xOri = 0, y = 0
				{2,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,9,0},
				{4,0,0,0,8,0,0,0,0},
				{0,0,7,0,0,0,0,0,0},
				{6,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0},
				{0,0,0,0,0,0,0,0,0}		// xOri = 8, y = 8
		};
		int[][] transformedSudokuBoard = new int[rank*rank][rank*rank];
		for(int x = 0; x < rank*rank; ++x)
			for(int y = 0; y < rank*rank; ++y)
				transformedSudokuBoard[x][y] = naturalSudokuBoard[y][x];
		sudoku.setBoard(transformedSudokuBoard);

		ActorRef<Teacher.Protocol> teacher = testKit.spawn(Teacher.create(
				new Teacher.CreateMsg("teacher1", sudoku, dummyGuardian.getRef())
		), "test4");

		// test is passed only because players got inspection messages fast enough
		teacher.tell(new Teacher.InspectChildDigitsMsg(dummyInspector.getRef()));
		Sudoku inspectionResults = dummyInspector.receiveMessage();

		sudoku.printNatural();
		System.out.println();
		inspectionResults.printNatural();

		assertEquals(sudoku, inspectionResults);
	}
}
