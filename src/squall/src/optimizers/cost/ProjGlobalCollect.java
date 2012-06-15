package optimizers.cost;

import java.util.ArrayList;
import java.util.List;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import visitors.jsql.AndVisitor;

/*
 * This is executed once before the dynamic Selinger approach has started.
 * We collect expressions appearing in FinalAgg(SELECT clause), including terms inside SUM and COUNT
 *   and from SelectOperator(WHERE clause) we collect OR expressions,
 *   because SelectOperator for this component is done before projections.
 * DOES NOT work with partial expressions - too complex and rarely necessary.
 *   (if we have (R.A +5) * 3 never R.A + 5 will be recognized as subexpression).
 */
public class ProjGlobalCollect {
    private List<Expression> _exprList;
    private List<OrExpression> _orExprs;

    private final List<SelectItem> _selectItems;
    private final Expression _whereExpr;

    public ProjGlobalCollect(List<SelectItem> selectItems, Expression whereExpr){
        _selectItems = selectItems;
        _whereExpr = whereExpr;
    }
    
    public void process(){
        _exprList =  new ArrayList<Expression>();
        _orExprs = new ArrayList<OrExpression>();
        processSelectClause(_selectItems);
        processWhereClause(_whereExpr);
    }

    /*
     * return expresssions from SELECT clause
     */
    public List<Expression> getExprList() {
        return _exprList;
    }

    /*
     * return OrExpressions from WHERE clause
     */
    public List<OrExpression> getOrExprs() {
        return _orExprs;
    }

    /*
     * SELECT clause - Final aggregation
     */
    public void processSelectClause(List<SelectItem> selectItems){
        for(SelectItem si: selectItems){
            if(si instanceof SelectExpressionItem){
                Expression selectExpr = ((SelectExpressionItem)si).getExpression();
                if(!(selectExpr instanceof Function)){
                    getExprList().add(selectExpr);
                }else{
                    Function fun = (Function) selectExpr;
                    if (fun.getName().equalsIgnoreCase("SUM") || fun.getName().equalsIgnoreCase("COUNT")){
                        //collect internal expressions
                        ExpressionList params = fun.getParameters();
                        if(params!=null){
                            getExprList().addAll(params.getExpressions());
                        }
                    }
                }
            }else{
                throw new RuntimeException("* is not supported in SELECT clause for Cost-Based otpimizer!");
                //not supported for now, as explained in IndexSelectItemsVisitor
                //either SELECT * FROM R join S or
                //SELECT R.* FROM R join S
            }
        }
    }

    /*
     * WHERE clause - SelectOperator
     * only interested in OR expressions, because AND expressions are already done by addOperator(SelectOperator)
     */
    public void processWhereClause(Expression whereExpr){
        if (whereExpr == null) return;

        AndVisitor andVisitor = new AndVisitor();
        whereExpr.accept(andVisitor);
        _orExprs = andVisitor.getOrExprs();
    }

}
