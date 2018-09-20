package tech.sourced.babelfish;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

// POD style value of the exported AST HashMap
public class MapNode
{
    public enum CommentType
    {
        LEADING,
        TRAILING,
        FREESTANDING
    }

    public class Location
    {
        private int lineStart;
        private int lineEnd;
        private int offsetStart;
        private int offsetLength;

        public Location(int lineStart, int lineEnd, int offsetStart, int offsetLength) {
            this.lineStart = lineStart;
            this.lineEnd = lineEnd;
            this.offsetStart = offsetStart;
            this.offsetLength = offsetLength;
        }

        public Location() {
            this.lineStart = -1;
            this.lineEnd = -1;
            this.offsetStart = -1;
            this.offsetLength = -1;
        }
    }

    // Holds leading, trailing and freestand comments related to a MapNode
    public class RelatedComments
    {
        public class CommentInfo
        {
            String text;
            boolean isBlockComment = false;
            public Location location;

            public CommentInfo(String text, boolean isBlockComment, int lineStart, int lineEnd,
                               int offsetStart, int offsetLength) {
                this.text = text;
                this.isBlockComment = isBlockComment;
                this.location = new Location(lineStart, lineEnd, offsetStart, offsetLength);
            }
        }
        public List<CommentInfo> leading = new ArrayList<CommentInfo>();
        public List<CommentInfo> trailing = new ArrayList<CommentInfo>();
        public List<CommentInfo> freestanding = new ArrayList<CommentInfo>();

        public void addComment(CommentType type, String text, boolean isBlockComment, int lineStart,
                                      int lineEnd, int offsetStart, int offsetLength) {
            CommentInfo comment = new CommentInfo(text, isBlockComment, lineStart, lineEnd,
                    offsetStart, offsetLength);
            switch (type) {
                case LEADING:
                    comments.leading.add(comment);
                    break;
                case TRAILING:
                    comments.trailing.add(comment);
                    break;
                case FREESTANDING:
                    comments.freestanding.add(comment);
                    break;
            }
        }
    }

    public String IASTClassName;
    public HashMap<String, String> attributes = new HashMap<String, String>();
    public List<MapNode> children = new ArrayList<MapNode>();
    public RelatedComments comments = new RelatedComments();
    public Location location = new Location();

    public void setLocation(int lineStart, int lineEnd, int offsetStart, int offsetLength)
    {
        location = new Location(lineStart, lineEnd, offsetStart, offsetLength);
    }
}
