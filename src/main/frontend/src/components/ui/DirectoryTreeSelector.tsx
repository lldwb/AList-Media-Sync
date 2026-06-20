// ===================================================================
// 树状目录浏览组件 — 内联展开式路径选择器
// props: engineId, value, onChange, placeholder
// ===================================================================
import { useState, useCallback, useReducer } from 'react';
import { api } from '@/api/client';
import type { DirectoryEntryVO } from '@/types/api';

interface TreeNode {
  path: string;
  name: string;
  hasChildren: boolean;
  children: string[] | null; // null = 未加载，string[] = 子节点路径列表
  loading: boolean;
}

interface TreeState {
  nodes: Map<string, TreeNode>;
  rootChildren: string[] | null;
  rootLoading: boolean;
}

type TreeAction =
  | { type: 'LOAD_CHILDREN'; path: string; entries: DirectoryEntryVO[] }
  | { type: 'LOAD_ROOT'; entries: DirectoryEntryVO[] }
  | { type: 'START_LOADING'; path: string }
  | { type: 'START_ROOT_LOADING' };

function treeReducer(state: TreeState, action: TreeAction): TreeState {
  const newNodes = new Map(state.nodes);

  switch (action.type) {
    case 'START_ROOT_LOADING':
      return { ...state, rootLoading: true };

    case 'LOAD_ROOT': {
      const children = action.entries.map((e) => {
        const node: TreeNode = {
          path: e.path,
          name: e.name,
          hasChildren: e.hasChildren,
          children: null,
          loading: false,
        };
        newNodes.set(e.path, node);
        return e.path;
      });
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
      const children = action.entries.map((e) => {
        const child: TreeNode = {
          path: e.path,
          name: e.name,
          hasChildren: e.hasChildren,
          children: null,
          loading: false,
        };
        newNodes.set(e.path, child);
        return e.path;
      });
      newNodes.set(action.path, { ...node, children, loading: false });
      return { ...state, nodes: newNodes };
    }

    default:
      return state;
  }
}

interface DirectoryTreeSelectorProps {
  engineId: number;
  value?: string;
  onChange: (path: string) => void;
  placeholder?: string;
}

export function DirectoryTreeSelector({
  engineId,
  value,
  onChange,
  placeholder = '点击浏览选择路径',
}: DirectoryTreeSelectorProps) {
  const [open, setOpen] = useState(false);
  const [tree, dispatch] = useReducer(treeReducer, {
    nodes: new Map(),
    rootChildren: null,
    rootLoading: false,
  });

  const loadChildren = useCallback(
    async (path: string) => {
      dispatch({ type: 'START_LOADING', path });
      try {
        const entries = await api.get<DirectoryEntryVO[]>(
          `/storage-engines/${engineId}/directories?path=${encodeURIComponent(path)}`
        );
        dispatch({ type: 'LOAD_CHILDREN', path, entries });
      } catch {
        // 加载失败静默处理
        dispatch({ type: 'LOAD_CHILDREN', path, entries: [] });
      }
    },
    [engineId]
  );

  const openTree = useCallback(async () => {
    setOpen(true);
    if (tree.rootChildren === null) {
      dispatch({ type: 'START_ROOT_LOADING' });
      try {
        const entries = await api.get<DirectoryEntryVO[]>(
          `/storage-engines/${engineId}/directories?path=/`
        );
        dispatch({ type: 'LOAD_ROOT', entries });
      } catch {
        dispatch({ type: 'LOAD_ROOT', entries: [] });
      }
    }
  }, [engineId, tree.rootChildren]);

  const handleToggle = useCallback(
    (path: string) => {
      const node = tree.nodes.get(path);
      if (!node) return;
      if (node.children === null) {
        loadChildren(path);
      } else {
        // 折叠：将 children 设为 null
        const newNodes = new Map(tree.nodes);
        newNodes.set(path, { ...node, children: null });
        dispatch({ type: 'LOAD_CHILDREN', path, entries: [] }); // 通过 reducer 更新
      }
    },
    [tree.nodes, loadChildren]
  );

  const handleSelect = (path: string) => {
    onChange(path);
    setOpen(false);
  };

  const renderNode = (nodePath: string, depth: number = 0): React.ReactNode => {
    const node = tree.nodes.get(nodePath);
    if (!node) return null;

    const isExpanded = node.children !== null;
    const padLeft = depth * 20;

    return (
      <div key={nodePath}>
        <div
          className="flex items-center gap-1 py-1 px-2 hover:bg-gray-100 cursor-pointer rounded text-sm"
          style={{ paddingLeft: padLeft + 8 }}
        >
          {/* 展开/折叠 */}
          {node.hasChildren && (
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
          {/* 无子目录的占位 */}
          {!node.hasChildren && <span className="w-4 shrink-0" />}

          {/* 文件夹图标 */}
          <svg className="h-4 w-4 text-yellow-500 shrink-0" fill="currentColor" viewBox="0 0 24 24">
            <path d="M19.5 21a3 3 0 003-3v-4.5a3 3 0 00-3-3h-15a3 3 0 00-3 3V18a3 3 0 003 3h15zM1.5 10.146V6a3 3 0 013-3h5.379a2.25 2.25 0 011.59.659l2.122 2.121c.14.141.331.22.53.22H19.5a3 3 0 013 3v1.146A4.483 4.483 0 0019.5 9h-15a4.483 4.483 0 00-3 1.146z" />
          </svg>

          {/* 目录名（点击选中） */}
          <span
            className="flex-1 truncate hover:text-blue-600"
            onClick={() => handleSelect(nodePath)}
          >
            {node.name}
          </span>
        </div>

        {/* 子目录 */}
        {isExpanded && node.children && node.children.map((childPath) => renderNode(childPath, depth + 1))}
      </div>
    );
  };

  // 面包屑路径
  const pathSegments = value ? value.split('/').filter(Boolean) : [];

  return (
    <div className="relative">
      {/* 输入框 + 浏览按钮 */}
      <div className="flex items-center gap-2">
        <input
          type="text"
          value={value ?? ''}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
        />
        <button
          type="button"
          onClick={openTree}
          className="shrink-0 rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-600 hover:bg-gray-100"
          title="浏览目录"
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

      {/* 目录树面板 */}
      {open && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30" onClick={() => setOpen(false)}>
          <div
            className="bg-white rounded-lg shadow-xl max-w-md w-full mx-4 p-4 max-h-[70vh] flex flex-col"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between mb-3">
              <h4 className="text-sm font-semibold text-gray-900">选择目录</h4>
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
                  暂无子目录
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
