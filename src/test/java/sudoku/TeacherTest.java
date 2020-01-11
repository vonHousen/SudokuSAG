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
		ActorRef<SudokuSupervisor.Command> guardian = testKit.spawn(SudokuSupervisor.create(),"test3");
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
		TestProbe<Teacher.Protocol> dummyTeacher = testKit.createTestProbe();

		int rank = 3;
		Sudoku sudoku = new Sudoku(rank);
		int[][] naturalSudoku = {
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
		int[][] transformedSudoku = new int[rank*rank][rank*rank];
		for(int x = 0; x < rank*rank; ++x)
			for(int y = 0; y < rank*rank; ++y)
				transformedSudoku[x][y] = naturalSudoku[y][x];
		sudoku.setBoard(transformedSudoku);

		ActorRef<Teacher.Protocol> teacher = testKit.spawn(Teacher.create(
				new Teacher.CreateMsg("teacher1", sudoku, null)
		), "test4");


	}
}
