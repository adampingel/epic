package epic.parser.models

/*
 Copyright 2012 David Hall

 Licensed under the Apache License, Version 2.0 (the "License")
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/
import epic.framework._
import breeze.optimize.FirstOrderMinimizer.OptParams
import epic.parser.Parser
import epic.parser.ParseEval.Statistics
import breeze.linalg._
import breeze.optimize._
import epic.trees.{TreeInstance, AnnotatedLabel}

object ParserPipeline extends epic.parser.ParserPipeline {

  case class Params(modelFactory: ParserExtractableModelFactory[AnnotatedLabel, String],
                    opt: OptParams,
                    iterationsPerEval: Int = 50,
                    maxIterations: Int = 1002,
                    iterPerValidate: Int = 10,
                    randomize: Boolean = false);
  protected val paramManifest = manifest[Params]

  def trainParser(trainTrees: IndexedSeq[TreeInstance[AnnotatedLabel, String]],
                  validate: (Parser[AnnotatedLabel, String]) => Statistics, params: Params) = {
    import params._

    val model = modelFactory.make(trainTrees)

    val obj = new ModelObjective(model, trainTrees)
    val cachedObj = new CachedBatchDiffFunction(obj)
    val checking = new RandomizedGradientCheckingFunction(cachedObj, 1E-4, toString = {
      (i: Int) => model.featureIndex.get(i).toString
    })
    val init = obj.initialWeightVector(randomize)

    type OptState = FirstOrderMinimizer[DenseVector[Double], BatchDiffFunction[DenseVector[Double]]]#State
    def evalAndCache(pair: (OptState, Int)) {
      val (state, iter) = pair
      val weights = state.x
      if (iter % iterPerValidate == 0) {
        println("Validating...")
        val parser = model.extractParser(weights)
        println(validate(parser))
      }
    }

    for ((state, iter) <- params.opt.iterations(cachedObj, init).take(maxIterations).zipWithIndex.tee(evalAndCache _);
         if iter != 0 && iter % iterationsPerEval == 0) yield try {
      val parser = model.extractParser(state.x)
      ("LatentDiscrim-" + iter.toString, parser)
    } catch {
      case e => println(e); e.printStackTrace(); throw e
    }
  }
}