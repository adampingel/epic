package scalanlp.parser
/**
 * A DerivationScorer is a refined grammar that has been tuned to a particular sentence (if applicable).
 * It knows how to to two things: assign scores to rules and spans, and determine reachability of various refinements.
 *
 *
 * It might be nice to consider a refined grammar that doesn't need sentence-specific tuning, but
 * that interferes with integrating lexicalization into the framework.
 *
 * @author dlwh
 */
trait DerivationScorer[L, W] {


  /**
   * Scores the indexed label rule with refinenemnt ref, when it occurs at (begin, end). Can be used for s, or for a
   * "bottom" label. Mainly used for s.
   */
  def scoreSpan(begin: Int, end: Int, label: Int, ref: Int):Double

  /**
   * Scores the indexed [[scalanlp.trees.BinaryRule]] rule when it occurs at (begin, split, end)
   */
  def scoreBinaryRule(begin: Int, split: Int, end: Int, rule: Int, ref: Int):Double

  /**
   * Scores the indexed [[scalanlp.trees.UnaryRule]] rule when it occurs at (begin, end)
   */
  def scoreUnaryRule(begin: Int, end: Int, rule: Int, ref: Int):Double

  // stuff related to reachability
  /**
   * For a given span, what refinements to the label are allowed?
   * Refinements in general are in the range (0, numValidRefinements). This
   * method may return a subset.
   * @return array of valid refinements. Don't modify!
   */
  def validLabelRefinements(begin: Int, end: Int, label: Int):Array[Int]

  def numValidRefinements(label: Int):Int

  /**
   * For a given span and the parent's refinement, what refinements to the rule are allowed?
   * @param rule
   * @param begin
   * @param end
   * @return
   */
  def validRuleRefinementsGivenParent(begin: Int, end: Int, rule: Int, parentRef: Int):Array[Int]

  def validUnaryRuleRefinementsGivenChild(begin: Int, end: Int, rule: Int, childRef: Int):Array[Int]

  def leftChildRefinement(rule: Int, ruleRef: Int):Int
  def rightChildRefinement(rule: Int, ruleRef: Int):Int
  def parentRefinement(rule: Int, ruleRef: Int):Int
  def childRefinement(rule: Int, ruleRef: Int):Int

  def ruleRefinementFromRefinements(r: Int, refA: Int, refB: Int):Int
  def ruleRefinementFromRefinements(r: Int, refA: Int, refB: Int, refC: Int):Int
}

object DerivationScorer {
  def identity[L, W]: DerivationScorer[L, W] = UnrefinedDerivationScorer.identity[L, W]


  trait Factory[L, W] extends Serializable {
    def grammar: Grammar[L]
    def lexicon: Lexicon[L, W]

    def root = grammar.root
    def index = grammar.index
    def labelIndex = grammar.labelIndex
    def labelEncoder = grammar.labelEncoder

    def specialize(words: Seq[W]):Specialization
    type Specialization = DerivationScorer[L, W]
  }

}
