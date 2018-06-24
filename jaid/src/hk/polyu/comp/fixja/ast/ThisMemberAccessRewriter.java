package hk.polyu.comp.fixja.ast;

import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ITrackedNodePosition;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.RangeMarker;
import org.eclipse.text.edits.ReplaceEdit;

/**
 * Created by Max PEI.
 */
public class ThisMemberAccessRewriter extends ASTVisitor {

    private Document document;
    private Block block;
    private ASTRewrite rewrite;
    private RangeMarker rangeMarker;

    public String rewrite(Document originalDocument, Block block){
        this.document = new Document(originalDocument.get());
        this.block = (Block) ASTNode.copySubtree(block.getAST(), block);

        this.rewrite = ASTRewrite.create(block.getAST());
        ITrackedNodePosition nodePosition = rewrite.track(block);
        this.rangeMarker = new RangeMarker(nodePosition.getStartPosition(), nodePosition.getLength());

        block.accept(this);

        String blockText = "";
        try{
            rangeMarker.apply(this.document);
            blockText = this.document.get(rangeMarker.getOffset(), rangeMarker.getLength());
        }
        catch(BadLocationException e){
            throw new IllegalStateException("Failed to rewrite this member accesses.");
        }

        return blockText;
    }

    // =============================== visitor methods


    public boolean visit(MethodDeclaration node) {
        return false;
    }

    public boolean visit(MethodInvocation node) {
        if(node.getExpression() == null){
            // unqualified field access
            ITrackedNodePosition nodePosition = rewrite.track(node);
            ReplaceEdit replaceEdit;
            if(Modifier.isStatic(node.resolveMethodBinding().getModifiers())){
                replaceEdit = new ReplaceEdit(nodePosition.getStartPosition(), nodePosition.getLength(), node.resolveMethodBinding().getDeclaringClass().getName() + "." + node.toString());
                rangeMarker.addChild(replaceEdit);
            }
            else {
                replaceEdit = new ReplaceEdit(nodePosition.getStartPosition(), nodePosition.getLength(), "this." + node.toString());
                rangeMarker.addChild(replaceEdit);
            }
        }

        return true;
    }


    public boolean visit(SimpleName node) {
        IBinding binding = node.resolveBinding();
        if (binding instanceof IVariableBinding) {
            IVariableBinding variableBinding = (IVariableBinding) binding;
            if(variableBinding.isField()) {
                if ((!(node.getParent() instanceof FieldAccess) || node.getParent() instanceof FieldAccess && ((FieldAccess)node.getParent()).getExpression() == node)
                        && (!(node.getParent() instanceof QualifiedName) || node.getParent() instanceof QualifiedName && ((QualifiedName)node.getParent()).getQualifier() == node)) {
                    // unqualified field access
                    ITrackedNodePosition nodePosition = rewrite.track(node);
                    ReplaceEdit replaceEdit;
                    if(Modifier.isStatic(variableBinding.getModifiers())){
                        replaceEdit = new ReplaceEdit(nodePosition.getStartPosition(), nodePosition.getLength(), variableBinding.getDeclaringClass().getName() + "." + node.getIdentifier());
                        rangeMarker.addChild(replaceEdit);
                    }
                    else {
                        replaceEdit = new ReplaceEdit(nodePosition.getStartPosition(), nodePosition.getLength(), "this." + node.getIdentifier());
                        rangeMarker.addChild(replaceEdit);
                    }
                }
            }
        }
        return false;
    }

    public boolean visit(TypeDeclarationStatement node) {
        return false;
    }



}
