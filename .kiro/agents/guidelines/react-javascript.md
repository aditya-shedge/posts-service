# React & JavaScript Coding Standards

## Purpose
This document defines React and JavaScript specific coding standards for the **frontend service** in Splitwise-Lite. These guidelines complement the [Core Standards](core-standards.md) and should be followed for all frontend development.

---

## Table of Contents
1. [JavaScript Language Standards](#javascript-language-standards)
2. [React Best Practices](#react-best-practices)
3. [Project Structure](#project-structure)
4. [Component Design](#component-design)
5. [State Management](#state-management)
6. [API Integration](#api-integration)
7. [Routing](#routing)
8. [Forms and Validation](#forms-and-validation)
9. [Error Handling](#error-handling)
10. [Styling](#styling)
11. [Testing](#testing)
12. [Performance](#performance)
13. [Accessibility](#accessibility)
14. [Security](#security)

---

## JavaScript Language Standards

### Modern JavaScript (ES6+)

#### Use const/let, Never var
```javascript
// Good
const API_URL = 'http://localhost:8080/api';
let counter = 0;

// Bad
var counter = 0;
```

#### Arrow Functions
```javascript
// Good - concise arrow functions
const double = (x) => x * 2;
const greet = (name) => console.log(`Hello, ${name}`);

// Good - explicit return for complex logic
const calculateBalance = (expenses) => {
    const total = expenses.reduce((sum, exp) => sum + exp.amount, 0);
    return total / expenses.length;
};

// Avoid - unnecessary arrow function
const add = (a, b) => { return a + b; }; // Remove braces: (a, b) => a + b
```

#### Template Literals
```javascript
// Good
const message = `Group ${groupName} has ${memberCount} members`;
const url = `${API_BASE}/groups/${groupId}/expenses`;

// Bad
const message = 'Group ' + groupName + ' has ' + memberCount + ' members';
```

#### Destructuring
```javascript
// Good - object destructuring
const { id, name, members } = group;
const { data, error, loading } = useQuery();

// Good - array destructuring
const [count, setCount] = useState(0);
const [first, second, ...rest] = members;

// Good - function parameter destructuring
function GroupCard({ id, name, memberCount }) {
    return <div>{name} - {memberCount} members</div>;
}

// Bad - accessing properties repeatedly
function GroupCard(props) {
    return <div>{props.name} - {props.memberCount} members</div>;
}
```

#### Spread Operator
```javascript
// Good - copying arrays/objects
const newMembers = [...members, newMember];
const updatedGroup = { ...group, name: 'New Name' };

// Good - combining arrays
const allExpenses = [...groupAExpenses, ...groupBExpenses];

// Good - function arguments
Math.max(...numbers);
```

#### Default Parameters
```javascript
// Good
function fetchGroups(page = 0, size = 20) {
    return api.get(`/groups?page=${page}&size=${size}`);
}

// Bad
function fetchGroups(page, size) {
    page = page || 0;
    size = size || 20;
    return api.get(`/groups?page=${page}&size=${size}`);
}
```

#### Optional Chaining
```javascript
// Good - safe property access
const memberName = group?.members?.[0]?.name;
const email = user?.profile?.email ?? 'No email';

// Bad - manual null checks
const memberName = group && group.members && group.members[0] && group.members[0].name;
```

#### Nullish Coalescing
```javascript
// Good - only replaces null/undefined
const count = memberCount ?? 0;
const name = userName ?? 'Guest';

// Be careful with || operator
const count = memberCount || 0; // Replaces 0, '', false, etc.
```

### Promises and Async/Await

#### Prefer async/await over .then()
```javascript
// Good - async/await
async function loadGroup(groupId) {
    try {
        const group = await api.getGroup(groupId);
        const expenses = await api.getExpenses(groupId);
        return { group, expenses };
    } catch (error) {
        console.error('Failed to load group:', error);
        throw error;
    }
}

// Acceptable - simple promise chains
api.getGroup(groupId)
    .then(group => setGroup(group))
    .catch(error => setError(error));

// Bad - mixing async/await with .then()
async function loadGroup(groupId) {
    return api.getGroup(groupId).then(group => {
        return api.getExpenses(groupId).then(expenses => {
            return { group, expenses };
        });
    });
}
```

#### Parallel Promises
```javascript
// Good - parallel execution
async function loadGroupData(groupId) {
    const [group, expenses, balances] = await Promise.all([
        api.getGroup(groupId),
        api.getExpenses(groupId),
        api.getBalances(groupId)
    ]);
    return { group, expenses, balances };
}

// Bad - sequential (slower)
async function loadGroupData(groupId) {
    const group = await api.getGroup(groupId);
    const expenses = await api.getExpenses(groupId);
    const balances = await api.getBalances(groupId);
    return { group, expenses, balances };
}
```

### Array Methods

#### Use map, filter, reduce
```javascript
// Good - functional approach
const activeMembers = members.filter(m => m.isActive);
const memberNames = members.map(m => m.name);
const totalAmount = expenses.reduce((sum, exp) => sum + exp.amount, 0);

// Good - chaining
const activeMemberNames = members
    .filter(m => m.isActive)
    .map(m => m.name)
    .sort();

// Bad - imperative loop (when functional is clearer)
const activeMembers = [];
for (let i = 0; i < members.length; i++) {
    if (members[i].isActive) {
        activeMembers.push(members[i]);
    }
}
```

#### When to Use forEach vs map
```javascript
// Use map when transforming data
const doubled = numbers.map(n => n * 2);

// Use forEach for side effects (not transformations)
members.forEach(m => console.log(m.name));

// Don't use forEach when you need the result
// Bad
const doubled = [];
numbers.forEach(n => doubled.push(n * 2));
// Good
const doubled = numbers.map(n => n * 2);
```

---

## React Best Practices

### Functional Components

#### Always Use Functional Components (No Class Components)
```javascript
// Good - functional component
function GroupCard({ group }) {
    const [expanded, setExpanded] = useState(false);
    
    useEffect(() => {
        // Effect logic
    }, []);
    
    return <div>{group.name}</div>;
}

// Bad - class component (avoid)
class GroupCard extends React.Component {
    constructor(props) {
        super(props);
        this.state = { expanded: false };
    }
    
    render() {
        return <div>{this.props.group.name}</div>;
    }
}
```

#### Component Naming
```javascript
// Good - PascalCase for components
function GroupList() { }
function ExpenseForm() { }
function MemberAvatar() { }

// Bad
function groupList() { }
function expense_form() { }
```

### Props

#### Props Destructuring
```javascript
// Good - destructure in function signature
function GroupCard({ id, name, memberCount, onEdit }) {
    return (
        <div>
            <h3>{name}</h3>
            <p>{memberCount} members</p>
            <button onClick={() => onEdit(id)}>Edit</button>
        </div>
    );
}

// Acceptable - destructure in body for many props
function GroupCard(props) {
    const { id, name, memberCount, onEdit, onDelete, created, updated } = props;
    // ...
}

// Bad - accessing props.x repeatedly
function GroupCard(props) {
    return (
        <div>
            <h3>{props.name}</h3>
            <p>{props.memberCount} members</p>
        </div>
    );
}
```

#### PropTypes or TypeScript
```javascript
// Good - PropTypes for runtime validation
import PropTypes from 'prop-types';

function GroupCard({ id, name, memberCount }) {
    return <div>{name}</div>;
}

GroupCard.propTypes = {
    id: PropTypes.string.isRequired,
    name: PropTypes.string.isRequired,
    memberCount: PropTypes.number.isRequired,
};

// Better - use TypeScript (recommended for new code)
interface GroupCardProps {
    id: string;
    name: string;
    memberCount: number;
}

function GroupCard({ id, name, memberCount }: GroupCardProps) {
    return <div>{name}</div>;
}
```

#### Default Props
```javascript
// Good - default parameters
function Avatar({ size = 32, src, alt }) {
    return <img src={src} alt={alt} width={size} height={size} />;
}

// Also acceptable - defaultProps
Avatar.defaultProps = {
    size: 32,
};
```

### Hooks

#### Rules of Hooks
1. Only call hooks at the top level (not in loops, conditions, or nested functions)
2. Only call hooks from React function components or custom hooks

```javascript
// Good
function GroupDetail({ groupId }) {
    const [group, setGroup] = useState(null);
    const [loading, setLoading] = useState(true);
    
    useEffect(() => {
        loadGroup();
    }, [groupId]);
    
    async function loadGroup() {
        setLoading(true);
        const data = await api.getGroup(groupId);
        setGroup(data);
        setLoading(false);
    }
    
    if (loading) return <div>Loading...</div>;
    return <div>{group.name}</div>;
}

// Bad - conditional hook
function GroupDetail({ groupId }) {
    if (groupId) {
        const [group, setGroup] = useState(null); // ❌ Conditional hook
    }
}
```

#### useState
```javascript
// Good - descriptive state names
const [groups, setGroups] = useState([]);
const [loading, setLoading] = useState(false);
const [error, setError] = useState(null);

// Good - functional updates when depending on previous state
setCount(prevCount => prevCount + 1);

// Good - multiple related state values
const [formData, setFormData] = useState({
    name: '',
    email: '',
    amount: 0
});

// Bad - too many separate states for related data
const [name, setName] = useState('');
const [email, setEmail] = useState('');
const [amount, setAmount] = useState(0);
const [currency, setCurrency] = useState('USD');
// Better: combine into formData object
```

#### useEffect
```javascript
// Good - cleanup function
useEffect(() => {
    const subscription = api.subscribeToUpdates(groupId);
    
    return () => {
        subscription.unsubscribe(); // Cleanup
    };
}, [groupId]);

// Good - dependency array
useEffect(() => {
    if (groupId) {
        loadGroup(groupId);
    }
}, [groupId]); // Re-run when groupId changes

// Bad - missing dependency
useEffect(() => {
    loadGroup(groupId); // Uses groupId but not in dependency array
}, []); // ❌ Warning: missing dependency

// Good - empty dependency array for mount-only effect
useEffect(() => {
    console.log('Component mounted');
}, []);
```

#### Custom Hooks
```javascript
// Good - custom hook for reusable logic
function useGroup(groupId) {
    const [group, setGroup] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    
    useEffect(() => {
        async function fetchGroup() {
            try {
                setLoading(true);
                const data = await api.getGroup(groupId);
                setGroup(data);
            } catch (err) {
                setError(err);
            } finally {
                setLoading(false);
            }
        }
        
        if (groupId) {
            fetchGroup();
        }
    }, [groupId]);
    
    return { group, loading, error };
}

// Usage
function GroupDetail({ groupId }) {
    const { group, loading, error } = useGroup(groupId);
    
    if (loading) return <div>Loading...</div>;
    if (error) return <div>Error: {error.message}</div>;
    return <div>{group.name}</div>;
}
```

#### useCallback and useMemo
```javascript
// Use useCallback to memoize functions passed as props
function GroupList({ onGroupSelect }) {
    const [groups, setGroups] = useState([]);
    
    const handleSelect = useCallback((groupId) => {
        console.log('Selected:', groupId);
        onGroupSelect(groupId);
    }, [onGroupSelect]);
    
    return (
        <div>
            {groups.map(g => (
                <GroupCard key={g.id} group={g} onSelect={handleSelect} />
            ))}
        </div>
    );
}

// Use useMemo to memoize expensive calculations
function ExpenseList({ expenses }) {
    const totalAmount = useMemo(() => {
        return expenses.reduce((sum, exp) => sum + exp.amount, 0);
    }, [expenses]); // Only recalculate when expenses change
    
    return <div>Total: ${totalAmount}</div>;
}

// Don't overuse - only for expensive operations
// Bad - unnecessary memoization
const value = useMemo(() => props.value * 2, [props.value]); // Too simple to memoize
```

---

## Project Structure

### Current Structure
```
src/
├── App.js                # Root component, routing
├── index.js              # React entry point
├── utils.js              # Utility functions (API URL config)
├── components/
│   ├── GroupList.js      # List of groups
│   └── GroupDetail.js    # Single group view
└── services/
    └── api.js            # API client
```

### Recommended Structure (For Growth)
```
src/
├── App.js
├── index.js
├── components/
│   ├── common/           # Reusable components
│   │   ├── Button.js
│   │   ├── Input.js
│   │   └── Modal.js
│   ├── groups/
│   │   ├── GroupList.js
│   │   ├── GroupCard.js
│   │   ├── GroupDetail.js
│   │   └── CreateGroupForm.js
│   ├── expenses/
│   │   ├── ExpenseList.js
│   │   ├── ExpenseForm.js
│   │   └── ExpenseItem.js
│   └── layout/
│       ├── Header.js
│       ├── Footer.js
│       └── Navigation.js
├── hooks/                # Custom hooks
│   ├── useGroup.js
│   ├── useExpenses.js
│   └── useBalances.js
├── services/
│   ├── groupService.js
│   ├── expenseService.js
│   └── api.js
├── utils/
│   ├── formatters.js     # Date, currency formatting
│   ├── validators.js     # Input validation
│   └── constants.js      # App constants
└── styles/
    ├── global.css
    └── variables.css
```

### File Naming
- Components: PascalCase (`GroupList.js`, `ExpenseForm.js`)
- Utilities/Services: camelCase (`api.js`, `formatDate.js`)
- CSS: kebab-case or match component name (`group-list.css`)

---

## Component Design

### Component Size
- Keep components under 200 lines
- Extract complex logic into custom hooks
- Split large components into smaller ones

### Single Responsibility
```javascript
// Good - focused component
function GroupCard({ group, onSelect }) {
    return (
        <div className="group-card" onClick={() => onSelect(group.id)}>
            <h3>{group.name}</h3>
            <p>{group.members.length} members</p>
        </div>
    );
}

// Bad - component doing too much
function GroupPage() {
    // Handles: fetching data, displaying list, creating groups, editing, deleting
    // 500+ lines
    // Split into: GroupList, GroupForm, GroupDetail, etc.
}
```

### Composition Over Props Drilling
```javascript
// Good - component composition
function GroupList({ groups }) {
    return (
        <div>
            {groups.map(group => (
                <GroupCard key={group.id}>
                    <GroupHeader name={group.name} />
                    <GroupMembers members={group.members} />
                    <GroupActions groupId={group.id} />
                </GroupCard>
            ))}
        </div>
    );
}

// Bad - excessive prop drilling
function GroupList({ groups, onEdit, onDelete, onAddMember, onViewDetails }) {
    return (
        <div>
            {groups.map(group => (
                <GroupCard 
                    key={group.id}
                    group={group}
                    onEdit={onEdit}
                    onDelete={onDelete}
                    onAddMember={onAddMember}
                    onViewDetails={onViewDetails}
                />
            ))}
        </div>
    );
}
```

### Presentational vs Container Components

**Presentational** (dumb/stateless):
```javascript
// Just renders UI, receives data via props
function ExpenseItem({ expense }) {
    return (
        <div className="expense-item">
            <span>{expense.description}</span>
            <span>${expense.amount}</span>
        </div>
    );
}
```

**Container** (smart/stateful):
```javascript
// Manages data fetching and state
function ExpenseList({ groupId }) {
    const [expenses, setExpenses] = useState([]);
    const [loading, setLoading] = useState(true);
    
    useEffect(() => {
        async function fetchExpenses() {
            setLoading(true);
            const data = await api.getExpenses(groupId);
            setExpenses(data);
            setLoading(false);
        }
        fetchExpenses();
    }, [groupId]);
    
    if (loading) return <div>Loading...</div>;
    
    return (
        <div>
            {expenses.map(exp => (
                <ExpenseItem key={exp.id} expense={exp} />
            ))}
        </div>
    );
}
```

### Conditional Rendering
```javascript
// Good - early return
function GroupDetail({ group }) {
    if (!group) {
        return <div>Group not found</div>;
    }
    
    return <div>{group.name}</div>;
}

// Good - ternary for simple conditions
function Status({ isActive }) {
    return (
        <div className={isActive ? 'status-active' : 'status-inactive'}>
            {isActive ? 'Active' : 'Inactive'}
        </div>
    );
}

// Good - logical && for conditional rendering
function GroupCard({ group, showMembers }) {
    return (
        <div>
            <h3>{group.name}</h3>
            {showMembers && <MemberList members={group.members} />}
        </div>
    );
}

// Bad - nested ternaries
return (
    <div>
        {isLoading ? <Spinner /> : error ? <Error /> : data ? <Content /> : <Empty />}
    </div>
);
// Better
if (isLoading) return <Spinner />;
if (error) return <Error />;
if (!data) return <Empty />;
return <Content />;
```

### Lists and Keys
```javascript
// Good - use unique, stable keys
function GroupList({ groups }) {
    return (
        <div>
            {groups.map(group => (
                <GroupCard key={group.id} group={group} />
            ))}
        </div>
    );
}

// Bad - using index as key (only if list never changes)
{groups.map((group, index) => (
    <GroupCard key={index} group={group} />
))}

// Bad - missing key
{groups.map(group => (
    <GroupCard group={group} /> // ❌ Missing key
))}
```

---

## State Management

### Local State (useState)
Use for component-specific state that doesn't need to be shared.

```javascript
function ExpenseForm() {
    const [amount, setAmount] = useState('');
    const [description, setDescription] = useState('');
    
    const handleSubmit = (e) => {
        e.preventDefault();
        // Submit form
    };
    
    return (
        <form onSubmit={handleSubmit}>
            <input value={description} onChange={e => setDescription(e.target.value)} />
            <input value={amount} onChange={e => setAmount(e.target.value)} />
            <button type="submit">Add Expense</button>
        </form>
    );
}
```

### Lifting State Up
When multiple components need the same state, lift it to their common parent.

```javascript
// Parent component manages shared state
function GroupPage() {
    const [selectedMemberId, setSelectedMemberId] = useState(null);
    
    return (
        <div>
            <MemberList onSelect={setSelectedMemberId} />
            <MemberDetails memberId={selectedMemberId} />
        </div>
    );
}
```

### Context API (For Deep Props)
Use Context to avoid prop drilling for global/semi-global state.

```javascript
// Create context
const GroupContext = createContext();

// Provider component
function GroupProvider({ children, groupId }) {
    const [group, setGroup] = useState(null);
    const [loading, setLoading] = useState(true);
    
    useEffect(() => {
        async function fetchGroup() {
            const data = await api.getGroup(groupId);
            setGroup(data);
            setLoading(false);
        }
        fetchGroup();
    }, [groupId]);
    
    return (
        <GroupContext.Provider value={{ group, loading }}>
            {children}
        </GroupContext.Provider>
    );
}

// Custom hook to use context
function useGroupContext() {
    const context = useContext(GroupContext);
    if (!context) {
        throw new Error('useGroupContext must be used within GroupProvider');
    }
    return context;
}

// Usage
function GroupDetail() {
    const { group, loading } = useGroupContext();
    
    if (loading) return <div>Loading...</div>;
    return <div>{group.name}</div>;
}

function App() {
    return (
        <GroupProvider groupId="123">
            <GroupDetail />
        </GroupProvider>
    );
}
```

### When to Use Context vs Props
- **Props**: Default choice, data flows explicitly
- **Context**: Deeply nested components, theme, auth, language
- **Don't overuse**: Context makes component reuse harder

---

## API Integration

### API Service Layer

```javascript
// services/api.js
import axios from 'axios';
import { apiUrl } from '../utils';

const api = axios.create({
    baseURL: apiUrl,
    headers: {
        'Content-Type': 'application/json',
    },
});

// Request interceptor (add auth token, etc.)
api.interceptors.request.use(
    (config) => {
        // Add auth token if available
        const token = localStorage.getItem('token');
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => Promise.reject(error)
);

// Response interceptor (handle errors globally)
api.interceptors.response.use(
    (response) => response.data,
    (error) => {
        if (error.response?.status === 401) {
            // Handle unauthorized
            console.error('Unauthorized');
        }
        return Promise.reject(error);
    }
);

export default {
    // Groups
    getGroups: () => api.get('/api/groups'),
    getGroup: (id) => api.get(`/api/groups/${id}`),
    createGroup: (data) => api.post('/api/groups', data),
    
    // Members
    addMember: (groupId, member) => 
        api.post(`/api/groups/${groupId}/members`, member),
    
    // Expenses
    getExpenses: (groupId) => api.get(`/api/groups/${groupId}/expenses`),
    addExpense: (groupId, expense) => 
        api.post(`/api/groups/${groupId}/expenses`, expense),
    
    // Balances
    getBalances: (groupId) => api.get(`/api/groups/${groupId}/balances`),
    getSettlements: (groupId) => api.get(`/api/groups/${groupId}/settlements`),
};
```

### Data Fetching Pattern

```javascript
function GroupDetail({ groupId }) {
    const [group, setGroup] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    
    useEffect(() => {
        async function fetchGroup() {
            try {
                setLoading(true);
                setError(null);
                const data = await api.getGroup(groupId);
                setGroup(data);
            } catch (err) {
                setError(err.message || 'Failed to load group');
            } finally {
                setLoading(false);
            }
        }
        
        fetchGroup();
    }, [groupId]);
    
    if (loading) return <div>Loading...</div>;
    if (error) return <div>Error: {error}</div>;
    if (!group) return <div>Group not found</div>;
    
    return <div>{group.name}</div>;
}
```

### Optimistic Updates

```javascript
function ExpenseList({ groupId }) {
    const [expenses, setExpenses] = useState([]);
    
    async function addExpense(newExpense) {
        // Optimistic update - add to UI immediately
        const tempId = `temp-${Date.now()}`;
        const optimisticExpense = { ...newExpense, id: tempId };
        setExpenses(prev => [...prev, optimisticExpense]);
        
        try {
            // Send to server
            const savedExpense = await api.addExpense(groupId, newExpense);
            
            // Replace temp with real data
            setExpenses(prev => 
                prev.map(exp => exp.id === tempId ? savedExpense : exp)
            );
        } catch (error) {
            // Rollback on error
            setExpenses(prev => prev.filter(exp => exp.id !== tempId));
            alert('Failed to add expense');
        }
    }
    
    return (
        <div>
            {expenses.map(exp => <ExpenseItem key={exp.id} expense={exp} />)}
        </div>
    );
}
```

---

## Routing

### React Router Setup

```javascript
// App.js
import { BrowserRouter, Routes, Route, Link } from 'react-router-dom';
import GroupList from './components/GroupList';
import GroupDetail from './components/GroupDetail';

function App() {
    return (
        <BrowserRouter>
            <nav>
                <Link to="/">Home</Link>
            </nav>
            
            <Routes>
                <Route path="/" element={<GroupList />} />
                <Route path="/group/:groupId" element={<GroupDetail />} />
                <Route path="*" element={<NotFound />} />
            </Routes>
        </BrowserRouter>
    );
}
```

### Route Parameters

```javascript
import { useParams, useNavigate } from 'react-router-dom';

function GroupDetail() {
    const { groupId } = useParams();
    const navigate = useNavigate();
    
    const handleBack = () => {
        navigate('/');
    };
    
    return (
        <div>
            <button onClick={handleBack}>Back</button>
            <h1>Group {groupId}</h1>
        </div>
    );
}
```

---

## Forms and Validation

### Controlled Components

```javascript
function CreateGroupForm({ onSubmit }) {
    const [name, setName] = useState('');
    const [errors, setErrors] = useState({});
    
    const validate = () => {
        const newErrors = {};
        if (!name.trim()) {
            newErrors.name = 'Group name is required';
        } else if (name.length < 3) {
            newErrors.name = 'Name must be at least 3 characters';
        }
        return newErrors;
    };
    
    const handleSubmit = (e) => {
        e.preventDefault();
        
        const validationErrors = validate();
        if (Object.keys(validationErrors).length > 0) {
            setErrors(validationErrors);
            return;
        }
        
        onSubmit({ name });
        setName('');
        setErrors({});
    };
    
    return (
        <form onSubmit={handleSubmit}>
            <div>
                <input
                    type="text"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="Group name"
                />
                {errors.name && <span className="error">{errors.name}</span>}
            </div>
            <button type="submit">Create Group</button>
        </form>
    );
}
```

### Form State Management

```javascript
// For multiple fields, use object state
function ExpenseForm({ onSubmit }) {
    const [formData, setFormData] = useState({
        description: '',
        amount: '',
        paidBy: '',
    });
    
    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData(prev => ({
            ...prev,
            [name]: value
        }));
    };
    
    const handleSubmit = (e) => {
        e.preventDefault();
        onSubmit(formData);
    };
    
    return (
        <form onSubmit={handleSubmit}>
            <input
                name="description"
                value={formData.description}
                onChange={handleChange}
            />
            <input
                name="amount"
                type="number"
                value={formData.amount}
                onChange={handleChange}
            />
            <select name="paidBy" value={formData.paidBy} onChange={handleChange}>
                <option value="">Select member</option>
            </select>
            <button type="submit">Add Expense</button>
        </form>
    );
}
```

---

## Error Handling

### Error Boundaries

```javascript
class ErrorBoundary extends React.Component {
    constructor(props) {
        super(props);
        this.state = { hasError: false, error: null };
    }
    
    static getDerivedStateFromError(error) {
        return { hasError: true, error };
    }
    
    componentDidCatch(error, errorInfo) {
        console.error('Error caught by boundary:', error, errorInfo);
        // Log to error reporting service
    }
    
    render() {
        if (this.state.hasError) {
            return (
                <div>
                    <h2>Something went wrong</h2>
                    <p>{this.state.error?.message}</p>
                </div>
            );
        }
        
        return this.props.children;
    }
}

// Usage
function App() {
    return (
        <ErrorBoundary>
            <GroupList />
        </ErrorBoundary>
    );
}
```

### Try-Catch in Async Functions

```javascript
async function loadGroup(groupId) {
    try {
        setLoading(true);
        const group = await api.getGroup(groupId);
        setGroup(group);
    } catch (error) {
        console.error('Failed to load group:', error);
        setError(error.message || 'Failed to load group');
    } finally {
        setLoading(false);
    }
}
```

---

## Styling

### CSS Modules (Recommended)

```css
/* GroupCard.module.css */
.card {
    border: 1px solid #ddd;
    padding: 16px;
    border-radius: 8px;
}

.title {
    font-size: 18px;
    font-weight: bold;
}
```

```javascript
import styles from './GroupCard.module.css';

function GroupCard({ group }) {
    return (
        <div className={styles.card}>
            <h3 className={styles.title}>{group.name}</h3>
        </div>
    );
}
```

### Inline Styles (Use Sparingly)

```javascript
// OK for dynamic styles
function ProgressBar({ percentage }) {
    const barStyle = {
        width: `${percentage}%`,
        backgroundColor: percentage > 50 ? 'green' : 'red',
    };
    
    return <div className="progress-bar" style={barStyle} />;
}

// Avoid for static styles - use CSS instead
```

### Conditional Classes

```javascript
import classNames from 'classnames'; // or use manual approach

function Button({ primary, disabled, children }) {
    const className = classNames('btn', {
        'btn-primary': primary,
        'btn-disabled': disabled,
    });
    
    return <button className={className}>{children}</button>;
}

// Without library
function Button({ primary, disabled, children }) {
    const className = `btn ${primary ? 'btn-primary' : ''} ${disabled ? 'btn-disabled' : ''}`.trim();
    return <button className={className}>{children}</button>;
}
```

---

## Testing

### Component Testing with React Testing Library

```javascript
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import GroupList from './GroupList';
import api from '../services/api';

jest.mock('../services/api');

describe('GroupList', () => {
    test('renders groups after loading', async () => {
        const mockGroups = [
            { id: '1', name: 'Group 1', members: [] },
            { id: '2', name: 'Group 2', members: [] },
        ];
        
        api.getGroups.mockResolvedValue(mockGroups);
        
        render(<GroupList />);
        
        // Loading state
        expect(screen.getByText('Loading...')).toBeInTheDocument();
        
        // After loading
        await waitFor(() => {
            expect(screen.getByText('Group 1')).toBeInTheDocument();
            expect(screen.getByText('Group 2')).toBeInTheDocument();
        });
    });
    
    test('handles create group', async () => {
        api.getGroups.mockResolvedValue([]);
        api.createGroup.mockResolvedValue({ id: '1', name: 'New Group' });
        
        render(<GroupList />);
        
        const input = screen.getByPlaceholderText('Group name');
        const button = screen.getByText('Create');
        
        fireEvent.change(input, { target: { value: 'New Group' } });
        fireEvent.click(button);
        
        await waitFor(() => {
            expect(api.createGroup).toHaveBeenCalledWith({ name: 'New Group' });
        });
    });
});
```

### Testing Custom Hooks

```javascript
import { renderHook, waitFor } from '@testing-library/react';
import useGroup from './useGroup';
import api from '../services/api';

jest.mock('../services/api');

test('useGroup fetches group data', async () => {
    const mockGroup = { id: '1', name: 'Test Group' };
    api.getGroup.mockResolvedValue(mockGroup);
    
    const { result } = renderHook(() => useGroup('1'));
    
    expect(result.current.loading).toBe(true);
    
    await waitFor(() => {
        expect(result.current.loading).toBe(false);
        expect(result.current.group).toEqual(mockGroup);
    });
});
```

---

## Performance

### React.memo

```javascript
// Memoize component to prevent unnecessary re-renders
const ExpenseItem = React.memo(function ExpenseItem({ expense }) {
    return (
        <div>
            <span>{expense.description}</span>
            <span>${expense.amount}</span>
        </div>
    );
});

// Only re-renders if expense object changes
```

### Lazy Loading

```javascript
import { lazy, Suspense } from 'react';

const GroupDetail = lazy(() => import('./components/GroupDetail'));

function App() {
    return (
        <Suspense fallback={<div>Loading...</div>}>
            <GroupDetail />
        </Suspense>
    );
}
```

### Avoid Inline Functions in Render

```javascript
// Bad - creates new function on every render
function GroupList({ groups }) {
    return (
        <div>
            {groups.map(g => (
                <GroupCard 
                    key={g.id} 
                    group={g} 
                    onSelect={() => handleSelect(g.id)} // New function every render
                />
            ))}
        </div>
    );
}

// Good - use useCallback or pass stable reference
function GroupList({ groups, onSelect }) {
    return (
        <div>
            {groups.map(g => (
                <GroupCard 
                    key={g.id} 
                    group={g} 
                    onSelect={onSelect} // Stable reference
                />
            ))}
        </div>
    );
}
```

---

## Accessibility

### Semantic HTML

```javascript
// Good - semantic elements
function GroupCard({ group }) {
    return (
        <article>
            <h2>{group.name}</h2>
            <p>{group.members.length} members</p>
            <button>View Details</button>
        </article>
    );
}

// Bad - div soup
function GroupCard({ group }) {
    return (
        <div>
            <div>{group.name}</div>
            <div>{group.members.length} members</div>
            <div onClick={handleClick}>View Details</div>
        </div>
    );
}
```

### ARIA Attributes

```javascript
<button 
    aria-label="Delete group"
    onClick={handleDelete}
>
    <DeleteIcon />
</button>

<input 
    type="text"
    aria-label="Group name"
    aria-required="true"
    aria-invalid={!!errors.name}
/>
```

### Keyboard Navigation

```javascript
function Modal({ onClose }) {
    useEffect(() => {
        const handleEscape = (e) => {
            if (e.key === 'Escape') {
                onClose();
            }
        };
        
        document.addEventListener('keydown', handleEscape);
        return () => document.removeEventListener('keydown', handleEscape);
    }, [onClose]);
    
    return (
        <div role="dialog" aria-modal="true">
            {/* Modal content */}
        </div>
    );
}
```

---

## Security

### XSS Prevention

```javascript
// Good - React escapes by default
<div>{userInput}</div>

// Dangerous - avoid dangerouslySetInnerHTML
<div dangerouslySetInnerHTML={{ __html: userInput }} /> // ❌

// If you must use it, sanitize first
import DOMPurify from 'dompurify';
<div dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(userInput) }} />
```

### Sensitive Data

```javascript
// Don't store sensitive data in localStorage
localStorage.setItem('password', password); // ❌

// Use httpOnly cookies for auth tokens (set by server)
```

---

## Code Review Checklist

- [ ] **Components**: Functional components with hooks
- [ ] **Props**: Proper PropTypes or TypeScript types
- [ ] **State**: Using appropriate state management
- [ ] **Effects**: useEffect with correct dependencies
- [ ] **Keys**: Unique keys in lists
- [ ] **Accessibility**: Semantic HTML and ARIA labels
- [ ] **Error Handling**: Try-catch for async, error boundaries
- [ ] **Performance**: No unnecessary re-renders
- [ ] **Styling**: Consistent approach (CSS modules)
- [ ] **Testing**: Tests for important components

---

## Common Pitfalls

1. **Mutating state directly**: Always use setState
2. **Missing useEffect dependencies**: Include all used variables
3. **Inline functions in render**: Causes unnecessary re-renders
4. **Not cleaning up effects**: Memory leaks from subscriptions
5. **Using index as key**: Breaks when list order changes
6. **Over-using Context**: Makes components less reusable
7. **Not handling loading/error states**: Poor UX
8. **Fetching in render**: Should be in useEffect
9. **Deep nesting**: Hard to read and maintain
10. **console.log in production**: Remove debug logs

---

## References

- [React Documentation](https://react.dev/)
- [React Router](https://reactrouter.com/)
- [React Testing Library](https://testing-library.com/react)
- [JavaScript.info](https://javascript.info/)
- [Clean Code JavaScript](https://github.com/ryanmcdermott/clean-code-javascript)
