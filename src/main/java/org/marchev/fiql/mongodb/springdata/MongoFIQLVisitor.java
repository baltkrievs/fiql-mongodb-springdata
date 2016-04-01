package org.marchev.fiql.mongodb.springdata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import net.jcip.annotations.ThreadSafe;

import org.marchev.fiql.mongodb.springdata.exception.FIQLValidationException;
import org.springframework.data.mongodb.core.query.Criteria;

import cz.jirutka.rsql.parser.ast.AndNode;
import cz.jirutka.rsql.parser.ast.ComparisonNode;
import cz.jirutka.rsql.parser.ast.ComparisonOperator;
import cz.jirutka.rsql.parser.ast.LogicalNode;
import cz.jirutka.rsql.parser.ast.NoArgRSQLVisitorAdapter;
import cz.jirutka.rsql.parser.ast.Node;
import cz.jirutka.rsql.parser.ast.OrNode;

/**
 * Implementation of {@link cz.jirutka.rsql.parser.ast.RSQLVisitor} that
 * converts {@linkplain cz.jirutka.rsql.parser.RSQLParser RSQL query} to
 * MongoDB/Morphia {@link Criteria}.
 */
@ThreadSafe
public class MongoFIQLVisitor extends NoArgRSQLVisitorAdapter<Criteria> {

    private static final Map<ComparisonOperator, CriteriaOperator> OPERATORS_MAP = new HashMap<ComparisonOperator, CriteriaOperator>() {
        private static final long serialVersionUID = 7545733207099350554L;
    {
        put( MongoFIQLOperators.EQUAL,                 (criteria, arg)-> { return criteria.is(arg);  });
        put( MongoFIQLOperators.GREATER_THAN_OR_EQUAL, (criteria, arg)-> { return criteria.gte(arg); });
        put( MongoFIQLOperators.GREATER_THAN,          (criteria, arg)-> { return criteria.gt(arg);  });
        put( MongoFIQLOperators.LESS_THAN_OR_EQUAL,    (criteria, arg)-> { return criteria.lte(arg); });
        put( MongoFIQLOperators.LESS_THAN,             (criteria, arg)-> { return criteria.lt(arg);  });
        put( MongoFIQLOperators.NOT_EQUAL,             (criteria, arg)-> { return criteria.ne(arg);  });
        put( MongoFIQLOperators.IN,                    (criteria, arg)-> {return criteria.in(toList(arg));});
        put( MongoFIQLOperators.NOT_IN,                (criteria, arg)-> { return criteria.nin(toList(arg)); });
        put( MongoFIQLOperators.ALL,                   (criteria, arg)-> { return criteria.all(toList(arg)); });
        put( MongoFIQLOperators.LIKE,                  (criteria, arg)-> { return criteria.regex(arg.toString()); });
    }};

    public Criteria visit(AndNode node) {
        return joinChildrenNodesInCriteria(node);
    }

    public Criteria visit(OrNode node) {
        return joinChildrenNodesInCriteria(node);
    }
    
    private static Collection toList(Object arg) {
        if (arg instanceof Object[]) {
            return Arrays.stream((Object[]) arg)
                    .collect(Collectors.toList());
        }
        return Collections.singletonList(arg);
    }

    public Criteria visit(ComparisonNode node) {
        return createCriteria(node);
    }


    /**
     * Creates a field criteria for the given node and operator.
     *
     * @param node The comparison node to extract selector and argument from.
     * @return A field criteria for the given comparison.
     *
     * @throws FIQLValidationException If the node contains more or less than
     *         one argument, or a matching field for the selector cannot be found.
     */
    protected Criteria createCriteria(ComparisonNode node) {
        Criteria criteria = Criteria.where(extractCriteriaField(node));
        CriteriaOperator criteriaOperator = OPERATORS_MAP.get(node.getOperator());
        return criteriaOperator.apply(criteria, extractArguments(node));
    }

    private String extractCriteriaField(ComparisonNode node) {
            return node.getSelector();
    }

    private Object extractArguments(ComparisonNode node) {
        return node.getArguments().size() == 1 ? node.getArguments().get(0) : node.getArguments().toArray();
    }


    private Criteria joinChildrenNodesInCriteria(LogicalNode node) {
        Criteria criteria = new Criteria();
        List<Criteria> criteriaChain = new ArrayList<Criteria>();
        for (Node childNode : node) {
            criteriaChain.add(childNode.accept(this));
        }
        if (node instanceof OrNode) {
            criteria.orOperator(criteriaChain.toArray(new Criteria[criteriaChain.size()]));
        } else {
            criteria.andOperator(criteriaChain.toArray(new Criteria[criteriaChain.size()]));
        }
        return criteria;
    }
}
