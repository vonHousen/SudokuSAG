package sudoku;

public class Vector2d
{
    public final int x;
    public final int y;

    public Vector2d(int x, int y)
    {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return x == ((Vector2d) o).x && y == ((Vector2d) o).y ;
    }

    @Override
    public int hashCode() {
        final int prime = 523;
        return prime * x + y;
    }
}
