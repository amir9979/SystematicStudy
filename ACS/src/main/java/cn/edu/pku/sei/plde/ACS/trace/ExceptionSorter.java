package cn.edu.pku.sei.plde.ACS.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

import cn.edu.pku.sei.plde.ACS.localization.Suspicious;
import cn.edu.pku.sei.plde.ACS.utils.FileUtils;
import cn.edu.pku.sei.plde.ACS.utils.LineUtils;
import cn.edu.pku.sei.plde.ACS.utils.VariableUtils;
import cn.edu.pku.sei.plde.ACS.visible.model.VariableInfo;

/**
 * Created by yanrunfa on 16-4-16.
 */
public class ExceptionSorter {
    private final String _classSrc;
    private final String _className;
    @SuppressWarnings("unused")
	private final String _code;
    private final String[] _methodCode;
    @SuppressWarnings("unused")
	private Suspicious _suspicious;
    private List<ExceptionVariable> _variables = new ArrayList<>();
    private List<ExceptionVariable> _paramVariables = new ArrayList<>();
    private List<ExceptionVariable> _fieldVariables = new ArrayList<>();
    private List<ExceptionVariable> _localVariables = new ArrayList<>();
    private List<ExceptionVariable> _addonVariable = new ArrayList<>();
    private List<ExceptionVariable> _sortedVariable = new ArrayList<>();
    private List<List<ExceptionVariable>> _variableCombinations = new ArrayList<>();

    public ExceptionSorter(Suspicious suspicious, String statement) {
        _suspicious = suspicious;
        _classSrc = suspicious._srcPath;
        _className = suspicious.classname();
        _code = FileUtils.getCodeFromFile(_classSrc, _className);
        if (statement.contains("\n")){
            _methodCode = statement.split("\n");
        }
        else {
            _methodCode = new String[]{statement};
        }
    }


    public List<ExceptionVariable> sort(List<ExceptionVariable> exceptionVariables){
        _variables = exceptionVariables;
        @SuppressWarnings("unused")
		List<ExceptionVariable> result = new ArrayList<>();
        if (exceptionVariables.size() == 1){
            return exceptionVariables;
        }
        for (ExceptionVariable exceptionVariable: exceptionVariables){
            if (exceptionVariable.variable.isParameter){
                _paramVariables.add(exceptionVariable);
            }
            if (exceptionVariable.variable.isLocalVariable){
                _localVariables.add(exceptionVariable);
            }
            if (exceptionVariable.variable.isFieldVariable){
                _fieldVariables.add(exceptionVariable);
            }
            if (exceptionVariable.variable.isAddon){
                _addonVariable.add(exceptionVariable);
            }
        }
        sortBoundary();
        return _sortedVariable;
    }

    @SuppressWarnings("unused")
	private void boundaryCombination(){
        _variableCombinations = subsets(_sortedVariable);
        Collections.sort(_variableCombinations, new Comparator<List<ExceptionVariable>>() {
            @Override
            public int compare(List<ExceptionVariable> o1, List<ExceptionVariable> o2) {
                return Integer.valueOf(o1.size()).compareTo(o2.size());
            }
        });
    }

    private <T> List<List<T>> subsets(List<T> nums) {
        List<List<T>> res = new ArrayList<>();
        List<T> each = new ArrayList<>();
        helper(res, each, 0, nums);
        return res;
    }

    private <T> void helper(List<List<T>> res, List<T> each, int pos, List<T> n) {
        if (pos <= n.size() && each.size()> 0) {
            res.add(each);
        }
        if (res.size() > 15){
            return;
        }
        for (int i = pos; i < n.size(); i++) {
            each.add(n.get(i));
            helper(res, new ArrayList<>(each), i + 1, n);
            each.remove(each.size() - 1);
        }
        return;
    }

    private void sortBoundary(){
        TreeMap<Integer,ExceptionVariable> boundaryLevel = new TreeMap<>(new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return -o1.compareTo(o2);
            }
        });
        
        for (ExceptionVariable exceptionVariable: _variables){
            int lastAssignLine = getLastAssignLine(exceptionVariable.variable);

            if (lastAssignLine == -1){
                continue;
            }
            while (boundaryLevel.containsKey(lastAssignLine)){
                lastAssignLine++;
            }
            boundaryLevel.put(lastAssignLine,exceptionVariable);
        }
        for (ExceptionVariable exceptionVariable: boundaryLevel.values()){
            _sortedVariable.add(exceptionVariable);
        }

        for (ExceptionVariable exceptionVariable: _localVariables){
            if (!_sortedVariable.contains(exceptionVariable)){
                _sortedVariable.add(exceptionVariable);
            }
        }
        for (ExceptionVariable exceptionVariable: _paramVariables){
            if (!_sortedVariable.contains(exceptionVariable)){
                _sortedVariable.add(exceptionVariable);
            }
        }
        for (ExceptionVariable exceptionVariable: _fieldVariables){
            if (!_sortedVariable.contains(exceptionVariable)){
                _sortedVariable.add(exceptionVariable);
            }
        }
        for (ExceptionVariable exceptionVariable: _addonVariable){
            if (!_sortedVariable.contains(exceptionVariable)){
                _sortedVariable.add(exceptionVariable);
            }
        }
    }

    private int getLastAssignLine(VariableInfo info){
        int returnLine =-1;
        String variableName = info.variableName;
        if (variableName.endsWith(".null") || variableName.endsWith(".Comparable")){
            variableName = variableName.substring(0,variableName.indexOf("."));
        }
        if (variableName.contains("==")){
            variableName = variableName.split("==")[0];
        }
        for (int i = 0; i < _methodCode.length; i++){
            if (_methodCode[i].matches(".*"+variableName+"\\s*=.*") && !_methodCode[i].matches(".*\".*"+variableName+"\\s*=.*") && !_methodCode[i].contains("==")){
                returnLine = i;
            }
            else if (_methodCode[i].trim().contains(variableName) && VariableUtils.isExpression(info) && LineUtils.isBoundaryLine(_methodCode[i])){
                returnLine = i;
            }
        }
        return returnLine;
    }
}
