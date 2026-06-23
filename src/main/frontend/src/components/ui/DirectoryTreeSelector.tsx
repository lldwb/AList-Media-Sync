// ===================================================================
// 树状浏览组件 — 支持"目录选择"和"文件选择"两种模式
// props: engineId, value, onChange, placeholder, mode='directory'|'file'
// ===================================================================
import { useState, useCallback, useReducer } from 'react';
import { api } from '@/api/client';
import type { DirectoryEntryVO, FileEntryVO } from '@/types/api';

interface TreeNode {
  path: string;
  name: string;
  /** 是否为目录；文件模式下文件节点 false，目录节点 true */
  isDirectory: boolean;
  /** 是否包含子目录（用于决定是否展示展开箭头） */
  hasChildren: boolean;
  /** 子节点路径列表；null = 未加载 */
  children: string[] | null;
  loading: boolean;
}

interface TreeState {
  nodes: Map<string, TreeNode>;
  rootChildren: string[] | null;
  rootLoading: boolean;
}

type TreeAction =
  | { type: 'LOAD_CHILDREN'; path: string; nodes: TreeNode[] }
  | { type: 'LOAD_ROOT'; nodes: TreeNode[] }
  | { type: 'START_LOADING'; path: string }
  | { type: 'START_ROOT_LOADING' }
  | { type: 'TOGGLE_COLLAPSE'; path: string };

function treeReducer(state: TreeState, action: TreeAction): TreeState {
  const newNodes = new Map(state.nodes);

  switch (action.type) {
    case 'START_ROOT_LOADING':
      return { ...state, rootLoading: true };

    case 'LOAD_ROOT': {
      const seen = new Set<string>();
      const children: string[] = [];
      for (const n of action.nodes) {
        if (seen.has(n.path)) continue;
        seen.add(n.path);
        newNodes.set(n.path, n);
        children.push(n.path);
      }
      return { nodes: newNodes, rootChildren: children, rootLoading: false };
    }

    case 'START_LOADING': {
      const node = newNodes.get(action.path);
      if (node) {
        newNodes.set(action.path, { ...node, loading: true });
      }
      return { ...state, nodes: newNodes };
    }

    case 'LOAD_CHILDREN': {
      const node = newNodes.get(action.path);
      if (!node) return state;
      const seen = new Set<string>();
      const children: string[] = [];
      for (const n of action.nodes) {
        if (seen.has(n.path)) continue;
        seen.add(n.path);
        newNodes.set(n.path, n);
        children.push(n.path);
      }
      newNodes.set(action.path, { ...node, children, loading: false });
      return { ...state, nodes: newNodes };
    }

    case 'TOGGLE_COLLAPSE': {
      const node = newNodes.get(action.path);
      if (!node) return state;
      newNodes.set(action.path, { ...node, children: null });
      return { ...state, nodes: newNodes };
    }

    default:
      return state;
  }
}

/** 选择模式：目录选择器 / 文件选择器 */
export type SelectorMode = 'directory' | 'file';

interface DirectoryTreeSelectorProps {
  engineId: number;
  value?: string;
  onChange: (path: string) => void;
  placeholder?: string;
  /** 禁用浏览按钮（如引擎未选择时） */
  disabled?: boolean;
  /**
   * 选择模式：
   * - 'directory'（默认）：只展示目录，叶子节点点击即选中目录路径
   * - 'file'：展示目录 + 文件，仅文件可作为最终选择项；目录仅用于展开浏览
   */
  mode?: SelectorMode;
}

/** 将后端 DirectoryEntryVO 转换为通用 TreeNode（目录节点） */
function dirToNode(e: DirectoryEntryVO): TreeNode {
  return {
    path: e.path,
    name: e.name,
    isDirectory: true,
    hasChildren: e.hasChildren,
    children: null,
    loading: false,
  };
}

/**
 * 将后端 FileEntryVO 转换为通用 TreeNode
 * 注意：FileEntry 接口未提供 hasChildren 字段，目录节点保守地设为 true（用户可尝试展开，子目录为空时显示"暂无"）。
 */
function entryToNode(e: FileEntryVO): TreeNode {
  return {
    path: e.path,
    name: e.name,
    isDirectory: e.isDirectory,
    hasChildren: e.isDirectory,
    children: null,
    loading: false,
  };
}

export function DirectoryTreeSelector({
  engineId,
  value,
  onChange,
  placeholder,
  disabled = false,
  mode = 'directory',
}: DirectoryTreeSelectorProps) {
  const [open, setOpen] = useState(false);
  const [tree, dispatch] = useReducer(treeReducer, {
    nodes: new Map(),
    rootChildren: null,
    rootLoading: false,
  });

  const fetchNodes = useCallback(
    async (path: string): Promise<TreeNode[]> => {
      // 根据模式选择接口：directory → /directories（仅目录），file → /entries（全部）
      if (mode === 'file') {
        const entries = await api.get<FileEntryVO[]>(
          `/storage-engines/${engineId}/entries?path=${encodeURIComponent(path)}`
        );
        return entries.map(entryToNode);
      }
      const entries = await api.get<DirectoryEntryVO[]>(
        `/storage-engines/${engineId}/directories?path=${encodeURIComponent(path)}`
      );
      return entries.map(dirToNode);
    },
    [engineId, mode]
  );

  const loadChildren = useCallback(
    async (path: string) => {
      dispatch({ type: 'START_LOADING', path });
      try {
        const nodes = await fetchNodes(path);
        dispatch({ type: 'LOAD_CHILDREN', path, nodes });
      } catch {
        dispatch({ type: 'LOAD_CHILDREN', path, nodes: [] });
      }
    },
    [fetchNodes]
  );

  const openTree = useCallback(async () => {
    setOpen(true);
    if (tree.rootChildren === null) {
      dispatch({ type: 'START_ROOT_LOADING' });
      try {
        const nodes = await fetchNodes('/');
        dispatch({ type: 'LOAD_ROOT', nodes });
      } catch {
        dispatch({ type: 'LOAD_ROOT', nodes: [] });
      }
    }
  }, [fetchNodes, tree.rootChildren]);

  const handleToggle = useCallback(
    (path: string) => {
      const node = tree.nodes.get(path);
      if (!node) return;
      if (node.children === null) {
        loadChildren(path);
      } else {
        dispatch({ type: 'TOGGLE_COLLAPSE', path });
      }
    },
    [tree.nodes, loadChildren]
  );

  /**
   * 选择节点：
   * - directory 模式：任何节点都可选（叶子或带子目录的目录）
   * - file 模式：只能选文件节点；点击目录名仅触发展开
   */
  const handleSelect = (node: TreeNode) => {
    if (mode === 'file') {
      if (!node.isDirectory) {
        onChange(node.path);
        setOpen(false);
      } else {
        handleToggle(node.path);
      }
      return;
    }
    onChange(node.path);
    setOpen(false);
  };

  const MAX_DEPTH = 50;
  const renderNode = (nodePath: string, depth: number = 0, ancestors: Set<string> = new Set()): React.ReactNode => {
    // 防止无限递归：深度限制 + 循环引用检测
    if (depth > MAX_DEPTH) {
      console.warn(`[DirectoryTreeSelector] 超过最大深度 ${MAX_DEPTH}，停止渲染: ${nodePath}`);
      return null;
    }
    if (ancestors.has(nodePath)) {
      console.warn(`[DirectoryTreeSelector] 检测到循环路径引用，跳过: ${nodePath}`);
      return null;
    }

    const node = tree.nodes.get(nodePath);
    if (!node) return null;

    const isExpanded = node.children !== null;
    const padLeft = depth * 20;
    const nextAncestors = new Set(ancestors);
    nextAncestors.add(nodePath);

    // 文件节点在 file 模式下点击高亮当前已选中
    const isSelected = mode === 'file' && !node.isDirectory && node.path === value;

    return (
      <div key={nodePath}>
        <div
          className={`flex items-center gap-1 py-1 px-2 cursor-pointer rounded text-sm ${
            isSelected ? 'bg-blue-50 text-blue-700' : 'hover:bg-gray-100'
          }`}
          style={{ paddingLeft: padLeft + 8 }}
        >
          {/* 展开/折叠（仅目录） */}
          {node.isDirectory && node.hasChildren && (
            <button
              type="button"
              onClick={(e) => { e.stopPropagation(); handleToggle(nodePath); }}
              className="shrink-0 w-4 h-4 flex items-center justify-center text-gray-400 hover:text-gray-600"
            >
              {node.loading ? (
                <svg className="animate-spin h-3 w-3" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
              ) : (
                <svg className={`h-3 w-3 transition-transform ${isExpanded ? 'rotate-90' : ''}`} fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 4.5l7.5 7.5-7.5 7.5" />
                </svg>
              )}
            </button>
          )}
          {/* 无可展开内容的占位 */}
          {!(node.isDirectory && node.hasChildren) && <span className="w-4 shrink-0" />}

          {/* 图标：目录 = 文件夹，文件 = 文档 */}
          {node.isDirectory ? (
            <svg className="h-4 w-4 text-yellow-500 shrink-0" fill="currentColor" viewBox="0 0 24 24">
              <path d="M19.5 21a3 3 0 003-3v-4.5a3 3 0 00-3-3h-15a3 3 0 00-3 3V18a3 3 0 003 3h15zM1.5 10.146V6a3 3 0 013-3h5.379a2.25 2.25 0 011.59.659l2.122 2.121c.14.141.331.22.53.22H19.5a3 3 0 013 3v1.146A4.483 4.483 0 0019.5 9h-15a4.483 4.483 0 00-3 1.146z" />
            </svg>
          ) : (
            <svg className="h-4 w-4 text-gray-400 shrink-0" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
            </svg>
          )}

          {/* 节点名（点击：目录展开 / 文件选中） */}
          <span
            className="flex-1 truncate hover:text-blue-600"
            onClick={() => handleSelect(node)}
          >
            {node.name}
          </span>
        </div>

        {/* 子节点 */}
        {isExpanded && node.children && node.children.map((childPath) => renderNode(childPath, depth + 1, nextAncestors))}
      </div>
    );
  };

  // 面包屑路径
  const pathSegments = value ? value.split('/').filter(Boolean) : [];
  const defaultPlaceholder = mode === 'file' ? '点击浏览选择文件' : '点击浏览选择目录';
  const dialogTitle = mode === 'file' ? '选择文件' : '选择目录';
  const emptyHint = mode === 'file' ? '当前目录为空' : '暂无子目录';

  return (
    <div className="relative">
      {/* 输入框 + 浏览按钮 */}
      <div className="flex items-center gap-2">
        <input
          type="text"
          value={value ?? ''}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder ?? defaultPlaceholder}
          className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
        />
        <button
          type="button"
          onClick={openTree}
          disabled={disabled}
          className={`shrink-0 rounded-md border px-3 py-2 text-sm ${
            disabled
              ? 'border-gray-200 text-gray-300 cursor-not-allowed bg-gray-50'
              : 'border-gray-300 text-gray-600 hover:bg-gray-100'
          }`}
          title={disabled ? '请先选择存储引擎' : dialogTitle}
        >
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 12.75V12A2.25 2.25 0 014.5 9.75h15A2.25 2.25 0 0121.75 12v.75m-8.69-6.44l-2.12-2.12a1.5 1.5 0 00-1.061-.44H4.5A2.25 2.25 0 002.25 6v12a2.25 2.25 0 002.25 2.25h15A2.25 2.25 0 0021.75 18V9a2.25 2.25 0 00-2.25-2.25h-5.379a1.5 1.5 0 01-1.06-.44z" />
          </svg>
        </button>
      </div>

      {/* 面包屑 */}
      {value && (
        <div className="flex items-center gap-1 mt-1 text-xs text-gray-500">
          <span className="cursor-pointer hover:text-blue-600" onClick={() => onChange('/')}>
            根目录
          </span>
          {pathSegments.map((seg, i) => (
            <span key={i} className="flex items-center gap-1">
              <span>/</span>
              <span
                className="cursor-pointer hover:text-blue-600"
                onClick={() => onChange('/' + pathSegments.slice(0, i + 1).join('/'))}
              >
                {seg}
              </span>
            </span>
          ))}
        </div>
      )}

      {/* 浏览面板 */}
      {open && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30" onClick={() => setOpen(false)}>
          <div
            className="bg-white rounded-lg shadow-xl max-w-md w-full mx-4 p-4 max-h-[70vh] flex flex-col"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between mb-3">
              <h4 className="text-sm font-semibold text-gray-900">{dialogTitle}</h4>
              <button
                type="button"
                onClick={() => setOpen(false)}
                className="text-gray-400 hover:text-gray-600"
              >
                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <div className="flex-1 overflow-y-auto border rounded-md">
              {tree.rootLoading ? (
                <div className="flex items-center justify-center py-8">
                  <svg className="animate-spin h-6 w-6 text-gray-400" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                  </svg>
                </div>
              ) : tree.rootChildren && tree.rootChildren.length > 0 ? (
                tree.rootChildren.map((childPath) => renderNode(childPath))
              ) : (
                <div className="py-8 text-center text-sm text-gray-400">
                  {emptyHint}
                </div>
              )}
            </div>

            {/* 当前选中路径 */}
            {value && (
              <div className="mt-3 pt-3 border-t text-sm">
                <span className="text-gray-500">已选择：</span>
                <span className="font-mono text-gray-700">{value}</span>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
