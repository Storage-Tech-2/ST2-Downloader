package com.andrews.gui.widget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileActionManager {
    
    public enum ActionType { MOVE, DELETE, RENAME, CREATE_FOLDER }

    public static class FileOperation {
        public final File source;
        public final File destination;
        public final boolean wasDirectory;

        public FileOperation(File source, File destination, boolean wasDirectory) {
            this.source = source;
            this.destination = destination;
            this.wasDirectory = wasDirectory;
        }
    }
    
    public static class FileAction {
        public final ActionType type;
        public final List<FileOperation> operations;

        public FileAction(ActionType type, List<FileOperation> operations) {
            this.type = type;
            this.operations = operations;
        }

        public FileAction(ActionType type, FileOperation operation) {
            this.type = type;
            this.operations = new ArrayList<>();
            this.operations.add(operation);
        }
    }
    
    private final List<FileAction> undoStack = new ArrayList<>();
    private final List<FileAction> redoStack = new ArrayList<>();
    private final int maxHistory;
    
    public FileActionManager(int maxHistory) {
        this.maxHistory = maxHistory;
    }
    
    public void addAction(FileAction action) {
        undoStack.add(action);
        redoStack.clear();

        while (undoStack.size() > maxHistory) {
            FileAction removed = undoStack.removeFirst();
            cleanupAction(removed);
        }
    }
    
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }
    
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }
    
    public String performUndo() {
        if (undoStack.isEmpty()) {
            return null;
        }
        
        FileAction action = undoStack.removeLast();
        String result = executeUndo(action);

        if (result != null) {
            limitStackSize(redoStack);
            redoStack.add(action);
        }
        
        return result;
    }
    
    public String performRedo() {
        if (redoStack.isEmpty()) {
            return null;
        }
        
        FileAction action = redoStack.removeLast();
        String result = executeRedo(action);

        if (result != null) {
            limitStackSize(undoStack);
            undoStack.add(action);
        }
        
        return result;
    }
    
    private void limitStackSize(List<FileAction> stack) {
        while (stack.size() >= maxHistory) {
            FileAction removed = stack.removeFirst();
            if (stack == undoStack) {
                cleanupAction(removed);
            }
        }
    }

    private String executeUndo(FileAction action) {
        return switch (action.type) {
            case MOVE -> undoMove(action);
            case DELETE -> undoDelete(action);
            case RENAME -> undoRename(action);
            case CREATE_FOLDER -> undoCreateFolder(action);
        };
    }

    private String executeRedo(FileAction action) {
        return switch (action.type) {
            case MOVE -> redoMove(action);
            case DELETE -> redoDelete(action);
            case RENAME -> redoRename(action);
            case CREATE_FOLDER -> redoCreateFolder(action);
        };
    }

    private String undoMove(FileAction action) {
        int successCount = moveFiles(action.operations, true);
        return successCount > 0 ? "Undid move of " + successCount + " item(s)" : null;
    }
    
    private String undoDelete(FileAction action) {
        int successCount = moveFiles(action.operations, true);
        return successCount > 0 ? "Restored " + successCount + " item(s)" : null;
    }
    
    private String undoRename(FileAction action) {
        if (action.operations.isEmpty()) return null;
        FileOperation op = action.operations.getFirst();

        if (renameFile(op.destination, op.source)) {
            return "Undid rename of \"" + op.destination.getName() + "\"";
        }
        return null;
    }
    
    private String undoCreateFolder(FileAction action) {
        if (action.operations.isEmpty()) return null;
        FileOperation op = action.operations.getFirst();

        if (op.destination.exists() && op.destination.isDirectory()) {
            File[] contents = op.destination.listFiles();
            if (contents == null || contents.length == 0) {
                if (op.destination.delete()) {
                    return "Undid creation of \"" + op.destination.getName() + "\"";
                }
            }
        }
        return null;
    }
    
    private String redoMove(FileAction action) {
        int successCount = moveFiles(action.operations, false);
        return successCount > 0 ? "Redid move of " + successCount + " item(s)" : null;
    }
    
    private String redoDelete(FileAction action) {
        int successCount = moveFiles(action.operations, false);
        return successCount > 0 ? "Deleted " + successCount + " item(s) again" : null;
    }
    
    private String redoRename(FileAction action) {
        if (action.operations.isEmpty()) return null;
        FileOperation op = action.operations.getFirst();

        if (renameFile(op.source, op.destination)) {
            return "Redid rename to \"" + op.destination.getName() + "\"";
        }
        return null;
    }
    
    private String redoCreateFolder(FileAction action) {
        if (action.operations.isEmpty()) return null;
        FileOperation op = action.operations.getFirst();

        if (!op.destination.exists() && op.destination.mkdir()) {
            return "Recreated folder \"" + op.destination.getName() + "\"";
        }
        return null;
    }
    
    private int moveFiles(List<FileOperation> operations, boolean reverse) {
        int successCount = 0;
        for (FileOperation op : operations) {
            File from = reverse ? op.destination : op.source;
            File to = reverse ? op.source : op.destination;

            if (renameFile(from, to)) {
                successCount++;
            }
        }
        return successCount;
    }

    private boolean renameFile(File from, File to) {
        return from.exists() && !to.exists() && from.renameTo(to);
    }

    private void cleanupAction(FileAction action) {
        if (action.type == ActionType.DELETE) {
            for (FileOperation op : action.operations) {
                if (op.destination.exists() && op.destination.getAbsolutePath().contains(".trash")) {
                    deleteRecursively(op.destination);
                }
            }
        }
    }
    
    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
    
    public String getUndoDescription() {
        if (undoStack.isEmpty()) return null;
        FileAction action = undoStack.getLast();
        return getActionDescription(action, "Undo");
    }
    
    public String getRedoDescription() {
        if (redoStack.isEmpty()) return null;
        FileAction action = redoStack.getLast();
        return getActionDescription(action, "Redo");
    }
    
    private String getActionDescription(FileAction action, String prefix) {
        return switch (action.type) {
            case MOVE -> prefix + " move";
            case DELETE -> prefix + " delete";
            case RENAME -> {
                if (!action.operations.isEmpty()) {
                    yield prefix + " rename of \"" + action.operations.getFirst().source.getName() + "\"";
                }
                yield prefix + " rename";
            }
            case CREATE_FOLDER -> {
                if (!action.operations.isEmpty()) {
                    yield prefix + " creation of \"" + action.operations.getFirst().destination.getName() + "\"";
                }
                yield prefix + " folder creation";
            }
        };
    }
}

