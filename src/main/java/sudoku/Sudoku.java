package sudoku;

import java.util.function.Function;

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

	public Sudoku(Sudoku sudoku)
	{
		this._rank = sudoku._rank;
		this._size = sudoku._size;
		this._board = sudoku._board.clone();
		this._mask = sudoku._mask.clone();
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

	public void insertDigit(int x, int y, int digit) {_board[x][y] = digit;}

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

	public int getTableCount()
	{
		return _size * _size;
	}

	public int getEmptyFieldsCount()
	{
		int count = 0;
		for (int i = 0; i < this._size; ++i)
		{
			for (int j = 0; j < this._size; ++j)
			{
				if (_board[i][j] == 0)
				{
					++count;
				}
			}
		}
		return count;
	}

	/** Prints the board of the Sudoku in natural, human-friendly style. */
	public void printNatural()
	{
		// Named lambda used as a subfunction for printing the horizontal line
		Function<String, String> getLine = (String c) -> {
			String line = "";
			for(int i = 0; i < _size + _rank + 1; ++i)
				line += c + " ";
			return line;
		};
		String row;

		for(int y = 0; y < _size; ++y)
		{
			if(y % _rank == 0)
				System.out.println(getLine.apply("-"));
			row = "";
			for(int x = 0; x < _size; ++x)
			{
				if(x % _rank == 0)
					row += "| ";
				row += _board[x][y] + " ";
			}
			row += "|";
			System.out.println(row);
		}
		System.out.println(getLine.apply("-"));
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		for(int y = 0; y < _size; ++y)
			for(int x = 0; x < _size; ++x)
				if(_board[x][y] != ((Sudoku) o).getDigit(x,y))
					return false;

		return true;
	}
}
