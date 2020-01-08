package sudoku;

import static java.lang.Math.sqrt;

/**
 * Class representing Sudoku riddle.
 */
public class Sudoku
{
	/** Size of a single (small) sudoku square (aka. block) */
	private final int _rank;
	/** Board size */
	private final int _size;
	/** Current values of board fields */
	private int[][] _board;
	/** Array of flags indicating hard-coded board fields. If true, field cannot be modified. */
	private boolean[][] _mask;

	public Sudoku(int rank)
	{
		this._rank = rank;
		this._size = rank*rank;
		this._board = new int[this._size][this._size];
		this._mask = new boolean[this._size][this._size];
	}

	/** Custom exception thrown when trying to initialize board with incorrect size. */
	public static class IncorrectBoardSizeException extends RuntimeException
	{
		public IncorrectBoardSizeException(String msg)
		{
			super(msg);
		}
	}

	/** Custom exception thrown when a board digit is negative or greater than sudoku rank (0 indicates an empty field). */
	public static class DigitOutOfRangeException extends RuntimeException
	{
		public DigitOutOfRangeException(String msg)
		{
			super(msg);
		}
	}

	/** Custom exception thrown when trying to modify hard-coded sudoku field. */
	public static class DigitImmutableException extends RuntimeException
	{
		public DigitImmutableException(String msg)
		{
			super(msg);
		}
	}

	/**
	 * Set board default state.
	 * @param board		2d array of sudoku digits (0 means that the field is empty)
	 */
	public void setBoard(int[][] board)
	{
		if (board.length != _size || board[0].length != _size)
		{
			throw new Sudoku.IncorrectBoardSizeException("Board size doesn't match sudoku size");
		}
		for (int i = 0; i < _size; ++i)
		{
			for (int j = 0; j < _size; ++j)
			{
				if (board[i][j] >= 0 && board[i][j] <= _size)
				{
					_board[i][j] = board[i][j];
					_mask[i][j] = (board[i][j] == 0);
				}
				else
				{
					throw new Sudoku.DigitOutOfRangeException("Sudoku digit out of range");
				}
			}
		}
	}

	/**
	 * Reset board to default state.
	 * Sets all unmasked board fields to zero (empty).
	 */
	public void reset()
	{
		for (int i = 0; i < this._size; ++i)
		{
			for (int j = 0; j < this._size; ++j)
			{
				if (!_mask[i][j])
				{
					_board[i][j] = 0;
				}
			}
		}
	}

	public int getDigit(int x, int y) { return _board[x][y]; }

	public boolean getMask(int x, int y) { return _mask[x][y]; }

	public int getSize()
	{
		return _size;
	}

	public int getRank()
	{
		return _rank;
	}

	public int getPlayerCount()
	{
		return 3 * _size;
	}
}
