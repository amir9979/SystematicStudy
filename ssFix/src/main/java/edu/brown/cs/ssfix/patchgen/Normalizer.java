package edu.brown.cs.ssfix.patchgen;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.WhileStatement;

public class Normalizer {

	public static ASTNode normalize(ASTNode node) {

		AddBlockVisitor visitor = new AddBlockVisitor(node.getAST());
		node.accept(visitor);
		return node;
	}

	private static class AddBlockVisitor extends ASTVisitor {
		AST ast;

		public AddBlockVisitor(AST ast) {
			this.ast = ast;
		}

		@SuppressWarnings("unchecked")
		@Override
		public boolean visit(DoStatement do_stmt) {

			// Add block, if necessary
			Statement body_stmt = do_stmt.getBody();
			if (!(body_stmt instanceof Block)) {
				Block body_block = ast.newBlock();
				do_stmt.setBody(body_block);
				body_block.statements().add(body_stmt);
			}
			return true;
		}

		@SuppressWarnings("unchecked")
		@Override
		public boolean visit(EnhancedForStatement efs) {

			// Add block, if necessary
			Statement body_stmt = efs.getBody();
			if (!(body_stmt instanceof Block)) {
				Block body_block = ast.newBlock();
				efs.setBody(body_block);
				body_block.statements().add(body_stmt);
			}
			return true;
		}

		@SuppressWarnings("unchecked")
		@Override
		public boolean visit(ForStatement for_stmt) {

			// Add block, if necessary
			Statement body_stmt = for_stmt.getBody();
			if (!(body_stmt instanceof Block)) {
				Block body_block = ast.newBlock();
				for_stmt.setBody(body_block);
				body_block.statements().add(body_stmt);
			}
			return true;
		}

		@SuppressWarnings("unchecked")
		@Override
		public boolean visit(IfStatement if_stmt) {

			// Add block, if necessary
			Statement then_stmt = if_stmt.getThenStatement();
			Statement else_stmt = if_stmt.getElseStatement();
			if (!(then_stmt instanceof Block)) {
				Block then_block = ast.newBlock();
				if_stmt.setThenStatement(then_block);
				then_block.statements().add(then_stmt);
			}
			if (else_stmt != null && !(else_stmt instanceof Block)) {
				Block else_block = ast.newBlock();
				if_stmt.setElseStatement(else_block);
				else_block.statements().add(else_stmt);
			}
			return true;
		}

		@SuppressWarnings("unchecked")
		@Override
		public boolean visit(LabeledStatement label_stmt) {

			// Add block, if necessary
			Statement body_stmt = label_stmt.getBody();
			if (!(body_stmt instanceof Block)) {
				Block body_block = ast.newBlock();
				label_stmt.setBody(body_block);
				body_block.statements().add(body_stmt);
			}
			return true;
		}

		@Override
		public boolean visit(SwitchStatement switch_stmt) {

			return true;
		}

		@SuppressWarnings("unchecked")
		@Override
		public boolean visit(WhileStatement while_stmt) {

			// Add block, if necessary
			Statement body_stmt = while_stmt.getBody();
			if (!(body_stmt instanceof Block)) {
				Block body_block = ast.newBlock();
				while_stmt.setBody(body_block);
				body_block.statements().add(body_stmt);
			}
			return true;
		}
	}
}
