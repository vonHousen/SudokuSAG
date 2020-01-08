package sudoku;

public class Position
{
    public final int x;
    public final int y;

    public Position(int x, int y)
    {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return x == ((Position) o).x && y == ((Position) o).y ;
    }

    @Override
    public int hashCode() {
        final int prime = 523;
        return prime * x + y;
    }
}
