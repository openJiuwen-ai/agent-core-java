package examples.retrieval;

import java.util.List;

/**
 * Basic vector similarity utilities used by the retrieval examples.
 */
public final class VectorSimilarityUtils {

    private VectorSimilarityUtils() {
    }

    public static double cosineSimilarity(List<Float> left, List<Float> right) {
        validateSameSize(left, right);
        double dotProduct = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int index = 0; index < left.size(); index++) {
            double leftValue = left.get(index);
            double rightValue = right.get(index);
            dotProduct += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    public static double euclideanDistance(List<Float> left, List<Float> right) {
        validateSameSize(left, right);
        double squaredDistance = 0.0;
        for (int index = 0; index < left.size(); index++) {
            double delta = left.get(index) - right.get(index);
            squaredDistance += delta * delta;
        }
        return Math.sqrt(squaredDistance);
    }

    private static void validateSameSize(List<Float> left, List<Float> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            throw new IllegalArgumentException("Vectors must be non-empty");
        }
        if (left.size() != right.size()) {
            throw new IllegalArgumentException("Vectors must have the same dimension");
        }
    }
}