package tech.sourced.babelfish;

import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.c.*;
import org.eclipse.cdt.core.dom.ast.cpp.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

// Hold macro expansions and allows to check if a node if inside one
class MacroExpansionContainer
{
    private class MacroExpansionLocation implements Comparable<MacroExpansionLocation>
    {
        public String macroCodename;
        public int startOffset;
        public int endOffset;

        MacroExpansionLocation(String codeName, int startOffset, int endOffset)
        {
            this.macroCodename = codeName;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }

        @Override
            public int compareTo(MacroExpansionLocation other)
            {
                return this.startOffset < other.startOffset ? -1
                    : this.startOffset > other.startOffset ? 1
                    : 0;
            }
    }

    private List<MacroExpansionLocation> macroExpansions;
    // Used to reparent macro expansions as children of macroDefinitions since they're
    // separate lists on CDT.
    private Hashtable<IASTPreprocessorMacroDefinition, Vector<IASTNodeLocation>> macroDef2Locations;
    private int firstStartOffset;
    private int lastEndOffset;

    MacroExpansionContainer()
    {
        macroDef2Locations = new Hashtable<IASTPreprocessorMacroDefinition, Vector<IASTNodeLocation>>();
        macroExpansions = new Vector<MacroExpansionLocation>();
    }

    private void addSingleExpansion(String macroCodename, int startOffset, int endOffset)
    {
        firstStartOffset = Math.min(startOffset, firstStartOffset);
        lastEndOffset = Math.max(endOffset, lastEndOffset);
        macroExpansions.add(new MacroExpansionLocation(macroCodename, startOffset, endOffset));
    }

    public void add(IASTPreprocessorMacroExpansion exp)
    {
        IASTPreprocessorMacroDefinition	def = exp.getMacroDefinition();
        Vector<IASTNodeLocation> expLocations = new Vector<IASTNodeLocation>(Arrays.asList(exp.getNodeLocations()));
        Vector<IASTNodeLocation> prevLocations = macroDef2Locations.get(def);

        if (prevLocations == null) {
            macroDef2Locations.put(def, expLocations);
        } else {
            prevLocations.addAll(expLocations);
        }

        for (IASTNodeLocation expLoc : expLocations) {
            IASTFileLocation defLoc = def.getFileLocation();
            if (defLoc == null)
                continue;

            int defStartOffset = defLoc.getNodeOffset();

            String macroCodename = def.getName().toString() + "_" +
                String.valueOf(defStartOffset) + ":" +
                String.valueOf(defStartOffset + defLoc.getNodeLength());


            int expStartOffset = expLoc.getNodeOffset();
            addSingleExpansion(macroCodename, expStartOffset,
                    expStartOffset + expLoc.getNodeLength());
        }
    }

    public Vector<IASTNodeLocation> getMacroDefLocations(IASTPreprocessorMacroDefinition def)
    {
        return macroDef2Locations.get(def);
    }

    public void clearMap()
    {
        macroDef2Locations.clear();
    }

    // Call after all the expansions have been added. Sorts by macro.startOffset
    public void sortByStartOffset()
    {
        //macroExpansions.sort();
        Collections.sort(macroExpansions);
    }

    public String checkFromExpansion(IASTNode node)
    {
        IASTFileLocation loc = node.getFileLocation();
        if (loc == null)
            return null;

        int nodeStart = loc.getNodeOffset();
        if (nodeStart < firstStartOffset)
            return null;

        int nodeEnd = nodeStart + loc.getNodeLength();
        if (nodeEnd > lastEndOffset)
            return null;

        for (MacroExpansionLocation expLoc : macroExpansions) {
            if (nodeStart >= expLoc.startOffset && nodeEnd <= expLoc.endOffset)
                return expLoc.macroCodename;
        }

        return null;
    }
}
