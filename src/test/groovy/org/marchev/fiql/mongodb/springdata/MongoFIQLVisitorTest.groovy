package org.marchev.fiql.mongodb.springdata

import cz.jirutka.rsql.parser.RSQLParser
import org.springframework.data.mongodb.core.query.Query
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static MongoFIQLOperators.mongoOperators

class MongoFIQLVisitorTest extends Specification {

    @Shared mongoOperations = TestUtils.createMongoOperations()

    def query = new Query()
    def visitor = new MongoFIQLVisitor()


    // TODO: Throw FIQLValidationException when field could not be found

    // TODO: Add test which tests the selector conversions based on entity types.

    @Unroll
    def 'RSQL operator to Mongo query: #rsql'() {
        setup:
            def rootNode = parse(rsql)
        when:
            def criteria = rootNode.accept(visitor)
        then:
            criteria != null
            expected == criteria.criteriaObject
        where:
            rsql                    | expected
            'a==b'                  | [ a: 'b' ]
            'a!=b'                  | [ a: [ $ne:  'b']]
            'a=gt=b'                | [ a: [ $gt:  'b']]
            'a=ge=b'                | [ a: [ $gte: 'b']]
            'a=lt=b'                | [ a: [ $lt:  'b']]
            'a=le=b'                | [ a: [ $lte: 'b']]
            'a=in=b'                | [ a: [ $in:  ['b']]]
            'a=out=b'               | [ a: [ $nin: ['b']]]
            'a=in=(a,b,c)'          | [ a: [ $in:  ['a', 'b','c']]]
            'a=out=(b,c)'           | [ a: [ $nin: ['b','c']]]
            'a=all=(x,y,z)'         | [ a: [ $all: ['x','y','z']]]
            'a==b,b==c'             | [ $or : [[ a: 'b'], [b: 'c']]]
            'a==b;b==c'             | [ $and : [[ a: 'b'], [b: 'c']]]
            'a==b,b==c;c==d'        | [ $or : [[ a: 'b'], [ $and: [[b: 'c'], [c: 'd']]]]]
            '(a==b,b==c);c==d'      | [ $and : [[ $or: [[ a: 'b'], [b: 'c']]], [c: 'd']]]
    }

    // TODO: Selectors which are not the same as field names

    // TODO: Convert FIQL to Mongo query with <field>.$id when field is @Reference


    ////// Helpers ////////

    def parse(String rsql) {
        new RSQLParser(mongoOperators()).parse(rsql)
    }
}
