package scalanlp.parser.epic

import scalanlp.parser._
import features.{Feature, IndicatorFeature, WordShapeFeaturizer}
import projections.GrammarRefinements
import ParseChart.LogProbabilityParseChart
import scalala.tensor.dense.DenseVector
import scalala.tensor.sparse.SparseVector
import scalala.library.Library
import java.io.File
import io.Source
import scalala.tensor.Counter
import scalanlp.trees.{BinarizedTree, AnnotatedLabel}

class LatentParserModel[L, L3, W](featurizer: Featurizer[L3, W],
                                  reannotate: (BinarizedTree[L], Seq[W])=>BinarizedTree[L],
                                  val projections: GrammarRefinements[L, L3],
                                  grammar: Grammar[L],
                                  lexicon: Lexicon[L, W],
                                  initialFeatureVal: (Feature=>Option[Double]) = { _ => None}) extends ParserModel[L, W] {
  type L2 = L3
  type Inference = LatentParserInference[L, L2, W]

  val indexedFeatures: FeatureIndexer[L, L2, W]  = FeatureIndexer(grammar, lexicon, featurizer, projections)
  def featureIndex = indexedFeatures.index

  override def initialValueForFeature(f: Feature) = {
    initialFeatureVal(f) getOrElse (math.random * 1E-3)
  }

  def emptyCounts = new scalanlp.parser.ExpectedCounts(featureIndex)

  def inferenceFromWeights(weights: DenseVector[Double]) = {
    val lexicon = new FeaturizedLexicon(weights, indexedFeatures)
    val grammar = FeaturizedGrammar(this.grammar, this.lexicon, projections, weights, indexedFeatures, lexicon)

    new LatentParserInference(indexedFeatures, reannotate, grammar, projections)
  }

  def extractParser(weights: DenseVector[Double]):ChartParser[L, W] = {
    SimpleChartParser(inferenceFromWeights(weights).grammar)
  }

  def expectedCountsToObjective(ecounts: ExpectedCounts) = {
    (ecounts.loss, ecounts.counts)
  }

}

case class LatentParserInference[L, L2, W](featurizer: DerivationFeaturizer[L, W, Feature],
                                           reannotate: (BinarizedTree[L], Seq[W])=>BinarizedTree[L],
                                           grammar: DerivationScorer.Factory[L, W],
                                           projections: GrammarRefinements[L, L2]) extends ParserInference[L, W] {

  // E[T-z|T, params]
  def goldCounts(ti: TreeInstance[L, W], grammar: DerivationScorer[L, W]) = {
    val reannotated = reannotate(ti.tree, ti.words)
    val ecounts = LatentTreeMarginal(this.grammar.grammar, grammar, projections.labels, ti.words, reannotated).expectedCounts(featurizer)

    ecounts
  }

}

case class LatentParserModelFactory(baseParser: ParserParams.BaseParser,
                                    substates: File = null,
                                    numStates: Int = 2,
                                    oldWeights: File = null,
                                    splitFactor: Int = 1) extends ParserModelFactory[AnnotatedLabel, String] {
  type MyModel = LatentParserModel[AnnotatedLabel, (AnnotatedLabel, Int), String]

  def split(x: AnnotatedLabel, counts: Map[AnnotatedLabel, Int], numStates: Int) = {
    for(i <- 0 until counts.getOrElse(x, numStates)) yield (x, i)
  }

  def unsplit(x: (AnnotatedLabel, Int)) = x._1

  def make(trainTrees: IndexedSeq[TreeInstance[AnnotatedLabel, String]]) = {
    val (xbarWords, xbarBinaries, xbarUnaries) = this.extractBasicCounts(trainTrees.map(_.mapLabels(_.baseAnnotatedLabel)))

    val (xbarParser,xbarLexicon) = baseParser.xbarGrammar(trainTrees)

    val substateMap = if(substates != null && substates.exists) {
      val in = Source.fromFile(substates).getLines()
      val pairs = for( line <- in) yield {
        val split = line.split("\\s+")
        AnnotatedLabel(split(0)) -> split(1).toInt
      }
      pairs.toMap + (xbarParser.root -> 1)
    } else {
      Map(xbarParser.root -> 1)
    }

    val gen = new WordShapeFeaturizer(Library.sum(xbarWords))
    def labelFlattener(l: (AnnotatedLabel, Int)) = {
      val basic = Seq(l)
      basic map(IndicatorFeature)
    }
    val feat = new SumFeaturizer[(AnnotatedLabel, Int), String](new RuleFeaturizer(labelFlattener _), new LexFeaturizer(gen, labelFlattener _))
    val indexedRefinements = GrammarRefinements(xbarParser, split(_:AnnotatedLabel, substateMap, numStates), unsplit)

    val featureCounter = if(oldWeights ne null) {
      val baseCounter = scalanlp.util.readObject[Counter[Feature, Double]](oldWeights)
      baseCounter
    } else {
      Counter[Feature, Double]()
    }

    def reannotate(tree: BinarizedTree[AnnotatedLabel], words: Seq[String]) = tree.map(_.baseAnnotatedLabel)
    new LatentParserModel[AnnotatedLabel, (AnnotatedLabel, Int), String](feat,
                                                                      reannotate,
                                                                      indexedRefinements,
                                                                      xbarParser,
                                                                      xbarLexicon,
                                                                      {featureCounter.get(_)})
  }
}



