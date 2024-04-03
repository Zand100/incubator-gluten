/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.gluten.ras.rule

import org.apache.gluten.ras.{EnforcerRuleFactory, Property, PropertyDef, Ras}
import org.apache.gluten.ras.memo.Closure
import org.apache.gluten.ras.property.PropertySet

import scala.collection.mutable

trait EnforcerRule[T <: AnyRef] {
  def shift(node: T): Iterable[T]
  def shape(): Shape[T]
  def constraint(): Property[T]
}

object EnforcerRule {
  def apply[T <: AnyRef](rule: RasRule[T], constraint: Property[T]): EnforcerRule[T] = {
    new EnforcerRuleImpl(rule, constraint)
  }

  def builtin[T <: AnyRef](constraint: Property[T]): EnforcerRule[T] = {
    new BuiltinEnforcerRule(constraint)
  }

  private class EnforcerRuleImpl[T <: AnyRef](
      rule: RasRule[T],
      override val constraint: Property[T])
    extends EnforcerRule[T] {
    override def shift(node: T): Iterable[T] = rule.shift(node)
    override def shape(): Shape[T] = rule.shape()
  }

  private class BuiltinEnforcerRule[T <: AnyRef](override val constraint: Property[T])
    extends EnforcerRule[T] {
    override def shift(node: T): Iterable[T] = List(node)
    override def shape(): Shape[T] = Shapes.fixedHeight(1)
  }
}

trait EnforcerRuleSet[T <: AnyRef] {
  def rulesOf(constraintSet: PropertySet[T]): Seq[RuleApplier[T]]
}

object EnforcerRuleSet {
  def apply[T <: AnyRef](ras: Ras[T], closure: Closure[T]): EnforcerRuleSet[T] = {
    new EnforcerRuleSetImpl(ras, closure)
  }

  private def newEnforcerRuleFactory[T <: AnyRef](
      ras: Ras[T],
      propertyDef: PropertyDef[T, _ <: Property[T]]): EnforcerRuleFactory[T] = {
    ras.propertyModel.newEnforcerRuleFactory(propertyDef)
  }

  private class EnforcerRuleSetImpl[T <: AnyRef](ras: Ras[T], closure: Closure[T])
    extends EnforcerRuleSet[T] {
    private val factoryBuffer =
      mutable.Map[PropertyDef[T, _ <: Property[T]], EnforcerRuleFactory[T]]()
    private val buffer = mutable.Map[Property[T], Seq[RuleApplier[T]]]()

    override def rulesOf(constraintSet: PropertySet[T]): Seq[RuleApplier[T]] = {
      constraintSet.getMap.flatMap {
        case (constraintDef, constraint) =>
          buffer.getOrElseUpdate(
            constraint, {
              val factory =
                factoryBuffer.getOrElseUpdate(
                  constraintDef,
                  newEnforcerRuleFactory(ras, constraintDef))
              RuleApplier(ras, closure, EnforcerRule.builtin(constraint)) +: factory
                .newEnforcerRules(constraint)
                .map(rule => RuleApplier(ras, closure, EnforcerRule(rule, constraint)))
            }
          )
      }.toSeq
    }
  }
}