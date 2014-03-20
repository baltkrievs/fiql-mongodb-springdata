package cz.jirutka.rsql.mongodb.morphia

import cz.jirutka.rsql.mongodb.morphia.fixtures.ChildEntity
import cz.jirutka.rsql.mongodb.morphia.fixtures.RootEntity
import cz.jirutka.rsql.parser.RSQLParser
import cz.jirutka.rsql.parser.ast.ComparisonOp
import cz.jirutka.rsql.parser.ast.RSQLNodesFactory
import org.mongodb.morphia.query.FieldCriteria
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static org.mongodb.morphia.query.FilterOperator.*

class MorphiaRSQLVisitorTest extends Specification {

    @Shared dataStore = TestUtils.createDatastore()

    // Fake converter that returns the source value
    def fakeConverter = Stub(StringConverter) {
        convert(_, _) >> { val, type -> val }
    }

    def query = dataStore.createQuery(RootEntity)
    def visitor = new MorphiaRSQLVisitor(RootEntity, dataStore.mapper, fakeConverter)


    def 'construct from query object'() {
        when:
            def instance = new MorphiaRSQLVisitor(query, fakeConverter)
        then:
            instance.mapper == dataStore.mapper
            instance.entityClass == query.entityClass
    }

    @Unroll
    def 'should map operator: #rsqlOperator'() {
        setup:
            def args = mongoOperator in [IN, NOT_IN] ? ['x', 'y'] : 'x'
            def rootNode = new RSQLNodesFactory().createComparisonNode(rsqlOperator.toString(), 'a', args)
        and:
            def expected = dataStore.createQuery(RootEntity)
            expected.and(fieldCriteria('a', mongoOperator, args))
        when:
            query.and( rootNode.accept(visitor) )
        then:
            query.queryObject == expected.queryObject
        where:
            rsqlOperator     | mongoOperator
            ComparisonOp.EQ  | EQUAL
            ComparisonOp.NE  | NOT_EQUAL
            ComparisonOp.GT  | GREATER_THAN
            ComparisonOp.GE  | GREATER_THAN_OR_EQUAL
            ComparisonOp.LT  | LESS_THAN
            ComparisonOp.LE  | LESS_THAN_OR_EQUAL
            ComparisonOp.IN  | IN
            ComparisonOp.OUT | NOT_IN
    }

    @Unroll
    def 'throw RSQLValidationException when multiple arguments given to: #operator'() {
        setup:
            def rootNode = parse("a${operator}(x,y)")
        when:
            query.and( rootNode.accept(visitor) )
        then:
            thrown RSQLValidationException
        where:
            operator << ComparisonOp.values() - [ComparisonOp.IN, ComparisonOp.OUT]
    }

    @Unroll
    def 'determine field type for selector: #selector'() {
        expect:
            visitor.determineFieldType(selector) == type
        where:
            selector             | type
            'name'               | String
            'year'               | int
            'genres'             | String
            'director'           | ChildEntity
            'director.birthdate' | Date
            'actors.birthdate'   | Date
            'actors.movies.year' | int
    }

    def 'throw RSQLValidationException when field could not be found'() {
        setup:
            def rootNode = parse('illegal==666')
        when:
            rootNode.accept(visitor)
        then:
            thrown RSQLValidationException
    }

    def 'convert argument value through Converter'() {
        setup:
            def converter = Mock(StringConverter)
            def visitor = new MorphiaRSQLVisitor(RootEntity, dataStore.mapper, converter)

        when:
            parse('year==2014').accept(visitor)
        then:
            1 * converter.convert('2014', int)

        when:
            parse('genres=in=(sci-fi,thriller)').accept(visitor)
        then:
           1 * converter.convert(['sci-fi', 'thriller'], String)
    }


    @Unroll
    def 'convert complex RSQL to Criteria: #rsql'() {
        setup:
            def rootNode = parse(rsql)
        when:
            query.and( rootNode.accept(visitor) )
        then:
            query.queryObject == expected
        where:
            rsql                    | expected
            'a==u;b==v;c!=w'        | [ a:'u', b:'v', c: [ $ne:'w' ] ]
            'a=gt=u;a=lt=v;c==w'    | [ $and: [ [a:[$gt:'u']], [a:[$lt:'v']], [c:'w']] ]
            'a==u,b==v;c==w,d==x'   | [ $or: [ [a:'u'], [b:'v', c:'w'], [d:'x']] ]
            '(a=gt=u,a=le=v);c==d'  | [ $or: [[a:[$gt:'u']], [a:[$lte:'v']]], c:'d']
    }


    //////// Helpers ////////

    def query(Closure c) {
        def query = dataStore.createQuery(RootEntity)
        c.call(query)
        query
    }

    def fieldCriteria(field, operator, value) {
        new FieldCriteria(dataStore.createQuery(RootEntity), field, operator, value, false, false)
    }

    def parse(String rsql) {
        new RSQLParser().parse(rsql)
    }
}
