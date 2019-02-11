package io.appform.hope.core.visitors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import io.appform.hope.core.Evaluatable;
import io.appform.hope.core.TreeNode;
import io.appform.hope.core.VisitorAdapter;
import io.appform.hope.core.combiners.AndCombiner;
import io.appform.hope.core.combiners.OrCombiner;
import io.appform.hope.core.operators.*;
import io.appform.hope.core.values.*;
import lombok.Builder;
import lombok.Data;

import java.util.Objects;

/**
 *
 */
public class Evaluator {
    private static final ObjectMapper mapper = new ObjectMapper();

    public boolean evaluate(Evaluatable evaluatable, JsonNode node) {
        try {
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(evaluatable));
        }
        catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        final Configuration configuration = Configuration.builder()
                .jsonProvider(new JacksonJsonNodeJsonProvider())
                .options(Option.SUPPRESS_EXCEPTIONS)
                .build();
        return evaluatable.accept(new LogicEvaluator(new EvaluationContext(JsonPath.using(configuration).parse(node))));
    }

    @Data
    @Builder
    private static class EvaluationContext {
        private final DocumentContext jsonContext;
    }

    private class LogicEvaluator extends VisitorAdapter<Boolean> {

        private final EvaluationContext evaluationContext;

        public LogicEvaluator(
                EvaluationContext evaluationContext) {
            super(true);
            this.evaluationContext = evaluationContext;
        }

        public boolean evaluate(Evaluatable evaluatable) {
            return evaluatable.accept(this);
        }

        @Override
        public Boolean visit(AndCombiner andCombiner) {
            return andCombiner.getExpressions()
                    .stream()
                    .allMatch(expression -> expression.accept(new LogicEvaluator(evaluationContext)));
        }

        @Override
        public Boolean visit(OrCombiner orCombiner) {
            return orCombiner.getExpressions()
                    .stream()
                    .anyMatch(expression -> expression.accept(new LogicEvaluator(evaluationContext)));
        }

        @Override
        public Boolean visit(Equals equals) {
            return Objects.equals(equals.getLhs().accept(new ObjectValueEvaluator(evaluationContext)), equals.getRhs().accept(new ObjectValueEvaluator(
                    evaluationContext)));
        }

        @Override
        public Boolean visit(Greater greater) {
            final Number lhs = numericValue(evaluationContext, greater.getLhs(), 0);
            final Number rhs = numericValue(evaluationContext, greater.getRhs(), 0);
            return lhs.doubleValue() > rhs.doubleValue();
        }

        @Override
        public Boolean visit(GreaterEquals greaterEquals) {
            final Number lhs = numericValue(evaluationContext, greaterEquals.getLhs(), 0);
            final Number rhs = numericValue(evaluationContext, greaterEquals.getRhs(), 0);
            return lhs.doubleValue() >= rhs.doubleValue();
        }

        @Override
        public Boolean visit(Lesser lesser) {
            final Number lhs = numericValue(evaluationContext, lesser.getLhs(), 0);
            final Number rhs = numericValue(evaluationContext, lesser.getRhs(), 0);
            return lhs.doubleValue() < rhs.doubleValue();
        }

        @Override
        public Boolean visit(LesserEquals lesserEquals) {
            final Number lhs = numericValue(evaluationContext, lesserEquals.getLhs(), 0);
            final Number rhs = numericValue(evaluationContext, lesserEquals.getRhs(), 0);
            return lhs.doubleValue() < rhs.doubleValue();
        }

        @Override
        public Boolean visit(NotEquals notEquals) {
            return !Objects.equals(
                    notEquals.getLhs().accept(new ObjectValueEvaluator(evaluationContext)),
                    notEquals.getRhs().accept(new ObjectValueEvaluator(evaluationContext)));
        }

        @Override
        public Boolean visit(And and) {
            boolean lhs = booleanValue(evaluationContext, and.getLhs(), false);
            boolean rhs = booleanValue(evaluationContext, and.getRhs(), false);

            return lhs && rhs;
        }

        @Override
        public Boolean visit(Or or) {
            boolean lhs = booleanValue(evaluationContext, or.getLhs(), false);
            boolean rhs = booleanValue(evaluationContext, or.getRhs(), false);

            return lhs || rhs;
        }

        @Override
        public Boolean visit(Not not) {
            boolean operand = booleanValue(evaluationContext, not.getOperand(), false);
            return !operand;
        }

    }

    private class ObjectValueEvaluator extends VisitorAdapter<Object> {
        private final EvaluationContext evaluationContext;

        public ObjectValueEvaluator(EvaluationContext evaluationContext) {
            super(null);
            this.evaluationContext = evaluationContext;
        }

        @Override
        public Object visit(JsonPathValue jsonPathValue) {
            final JsonNode extractedNode = evaluationContext.getJsonContext().read(jsonPathValue.getPath());
            if(null != extractedNode) {
                if(extractedNode.isTextual()) {
                    return extractedNode.asText();
                }
                if(extractedNode.isBoolean()) {
                    return extractedNode.asBoolean();
                }
                if(extractedNode.isNumber()) {
                    return extractedNode.asDouble();
                }
                if(extractedNode.isPojo()) {
                    return extractedNode.isPojo();
                }
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public Object visit(ObjectValue objectValue) {
            return objectValue.getValue();
        }

        @Override
        public Object visit(NumericValue numericValue) {
            return numericValue.getValue();
        }

        @Override
        public Object visit(StringValue stringValue) {
            return stringValue.getValue();
        }

        @Override
        public Object visit(BooleanValue booleanValue) {
            return booleanValue.getValue();
        }

    }

/*
    private static <T> T  extractValue(BinaryOperator<T> operator) {

    }
*/

    private static Number numericValue(EvaluationContext evaluationContext, TreeNode node, Number defaultValue) {
        return node.accept(new VisitorAdapter<Number>(defaultValue) {
            @Override
            public Number visit(JsonPathValue jsonPathValue) {
                final JsonNode value = evaluationContext.getJsonContext()
                        .read(jsonPathValue.getPath());
                if(value.isNumber()) {
                    return value.asDouble();
                }
                return defaultValue;
            }

            @Override
            public Number visit(NumericValue numericValue) {
                final Number value = numericValue.getValue();
                if(null == value) {
                    final JsonPathValue pathValue = numericValue.getPathValue();
                    if(null != pathValue) {
                        return pathValue.accept(this);
                    }
                }
                return value;
            }
        });
    }

    private static Boolean booleanValue(EvaluationContext evaluationContext, TreeNode node, boolean defaultValue) {
        return node.accept(new VisitorAdapter<Boolean>(defaultValue) {
            @Override
            public Boolean visit(JsonPathValue jsonPathValue) {
                final JsonNode value = evaluationContext.getJsonContext()
                        .read(jsonPathValue.getPath());
                if(value.isBoolean()) {
                    return value.asBoolean();
                }
                return super.visit(jsonPathValue);
            }

            @Override
            public Boolean visit(BooleanValue booleanValue) {
                final Boolean value = booleanValue.getValue();
                if(null == value) {
                    final JsonPathValue pathValue = booleanValue.getPathValue();
                    if(null != pathValue) {
                        return pathValue.accept(this);
                    }
                }
                return value;
            }
        });
    }
}
