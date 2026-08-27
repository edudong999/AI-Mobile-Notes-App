package com.ai_photo.data.model.note_ai;

import java.util.List;

/** Recursive tree node used by mind map response. */
public class MindmapNode {
    public String label;
    public List<MindmapNode> children;
}