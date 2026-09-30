import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Switch from '@mui/material/Switch';
import Tab from '@mui/material/Tab';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Tabs from '@mui/material/Tabs';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import AddOutlined from '@mui/icons-material/AddOutlined';
import DeleteOutlineOutlined from '@mui/icons-material/DeleteOutlineOutlined';
import EditOutlined from '@mui/icons-material/EditOutlined';
import { toErrorMessage } from '@/api/axiosClient';
import { organisationApi } from '@/api/organisation';
import {
  catalogueApi,
  type CategoryPayload,
  type ProjectPayload,
  type TemplatePayload,
} from '@/api/work';
import { EmptyState } from '@/components/EmptyState';
import { TableSkeleton } from '@/components/LoadingState';
import { PageHeader } from '@/components/PageHeader';
import { formatDuration } from '@/utils/time';
import type { CategoryType, Priority, Project, Recurrence, TaskCategory, TaskTemplate } from '@/types/work';

const CATEGORY_TYPES: CategoryType[] = [
  'WORK',
  'MEETING',
  'BREAK',
  'ADMIN',
  'TRAINING',
  'SUPPORT',
  'OTHER',
];
const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const RECURRENCES: Recurrence[] = ['NONE', 'DAILY', 'WEEKLY', 'MONTHLY'];

const EMPTY_CATEGORY: CategoryPayload = {
  code: '',
  name: '',
  description: '',
  categoryType: 'WORK',
  productive: true,
  departmentId: null,
  colourHex: '',
  displayOrder: 0,
};

const EMPTY_TEMPLATE: TemplatePayload = {
  departmentId: null,
  taskCategoryId: 0,
  name: '',
  description: '',
  defaultEstimatedMinutes: null,
  defaultPriority: 'MEDIUM',
  recurrence: 'NONE',
  displayOrder: 0,
};

const EMPTY_PROJECT: ProjectPayload = {
  code: '',
  name: '',
  description: '',
  departmentId: null,
  clientName: '',
  startDate: null,
  endDate: null,
  status: 'ACTIVE',
  billable: false,
};

/**
 * The configuration behind the work log: what activities exist, what a typical
 * task looks like per department, and what time can be booked against.
 */
export function WorkCataloguePage() {
  const queryClient = useQueryClient();
  const [tab, setTab] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const [categoryDialog, setCategoryDialog] = useState(false);
  const [editingCategory, setEditingCategory] = useState<TaskCategory | null>(null);
  const [categoryForm, setCategoryForm] = useState<CategoryPayload>(EMPTY_CATEGORY);

  const [templateDialog, setTemplateDialog] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState<TaskTemplate | null>(null);
  const [templateForm, setTemplateForm] = useState<TemplatePayload>(EMPTY_TEMPLATE);

  const [projectDialog, setProjectDialog] = useState(false);
  const [editingProject, setEditingProject] = useState<Project | null>(null);
  const [projectForm, setProjectForm] = useState<ProjectPayload>(EMPTY_PROJECT);

  const departments = useQuery({
    queryKey: ['departments', 'options'],
    queryFn: organisationApi.departmentOptions,
  });
  const categories = useQuery({ queryKey: ['task-categories'], queryFn: catalogueApi.categories });
  const templates = useQuery({ queryKey: ['task-templates'], queryFn: () => catalogueApi.templates() });
  const projects = useQuery({ queryKey: ['projects'], queryFn: catalogueApi.projects });

  function invalidate(key: string) {
    void queryClient.invalidateQueries({ queryKey: [key] });
  }

  const saveCategory = useMutation({
    mutationFn: (payload: CategoryPayload) =>
      editingCategory
        ? catalogueApi.updateCategory(editingCategory.id, payload)
        : catalogueApi.createCategory(payload),
    onSuccess: () => {
      invalidate('task-categories');
      setCategoryDialog(false);
      setEditingCategory(null);
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const removeCategory = useMutation({
    mutationFn: (id: number) => catalogueApi.deactivateCategory(id),
    onSuccess: () => invalidate('task-categories'),
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const saveTemplate = useMutation({
    mutationFn: (payload: TemplatePayload) =>
      editingTemplate
        ? catalogueApi.updateTemplate(editingTemplate.id, payload)
        : catalogueApi.createTemplate(payload),
    onSuccess: () => {
      invalidate('task-templates');
      setTemplateDialog(false);
      setEditingTemplate(null);
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const removeTemplate = useMutation({
    mutationFn: (id: number) => catalogueApi.deactivateTemplate(id),
    onSuccess: () => invalidate('task-templates'),
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const saveProject = useMutation({
    mutationFn: (payload: ProjectPayload) =>
      editingProject
        ? catalogueApi.updateProject(editingProject.id, payload)
        : catalogueApi.createProject(payload),
    onSuccess: () => {
      invalidate('projects');
      setProjectDialog(false);
      setEditingProject(null);
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const removeProject = useMutation({
    mutationFn: (id: number) => catalogueApi.deactivateProject(id),
    onSuccess: () => invalidate('projects'),
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  return (
    <Box>
      <PageHeader
        eyebrow="Administration"
        title="Work catalogue"
        description="Activity categories, department task templates and projects. Changing a category's productive flag changes how future days are scored."
        actions={
          <Button
            variant="contained"
            startIcon={<AddOutlined sx={{ fontSize: 18 }} />}
            onClick={() => {
              setError(null);
              if (tab === 0) {
                setEditingCategory(null);
                setCategoryForm(EMPTY_CATEGORY);
                setCategoryDialog(true);
              } else if (tab === 1) {
                setEditingTemplate(null);
                setTemplateForm(EMPTY_TEMPLATE);
                setTemplateDialog(true);
              } else {
                setEditingProject(null);
                setProjectForm(EMPTY_PROJECT);
                setProjectDialog(true);
              }
            }}
          >
            {tab === 0 ? 'Add category' : tab === 1 ? 'Add template' : 'Add project'}
          </Button>
        }
      />

      {error && (
        <Alert severity="error" sx={{ mb: 4 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      <Card sx={{ mb: 4 }}>
        <Tabs value={tab} onChange={(_, value) => setTab(value)} sx={{ px: 3 }}>
          <Tab label={`Categories (${categories.data?.length ?? 0})`} />
          <Tab label={`Task templates (${templates.data?.length ?? 0})`} />
          <Tab label={`Projects (${projects.data?.length ?? 0})`} />
        </Tabs>
      </Card>

      <Card>
        {tab === 0 &&
          (categories.isLoading ? (
            <TableSkeleton rows={8} columns={5} />
          ) : (
            <TableContainer sx={{ overflowX: 'auto' }}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Category</TableCell>
                    <TableCell>Type</TableCell>
                    <TableCell>Counts as productive</TableCell>
                    <TableCell>Department</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {(categories.data ?? []).map((category) => (
                    <TableRow key={category.id} hover>
                      <TableCell>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2.5 }}>
                          <Box
                            sx={{
                              width: 10,
                              height: 10,
                              borderRadius: '50%',
                              bgcolor: category.colourHex ?? 'primary.main',
                            }}
                          />
                          <Box>
                            <Typography sx={{ fontSize: 13, fontWeight: 600 }}>{category.name}</Typography>
                            <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                              {category.code}
                            </Typography>
                          </Box>
                        </Box>
                      </TableCell>
                      <TableCell sx={{ textTransform: 'capitalize' }}>
                        {category.categoryType.toLowerCase()}
                      </TableCell>
                      <TableCell>
                        <Chip
                          size="small"
                          label={category.productive ? 'productive' : 'not productive'}
                          color={category.productive ? 'success' : 'default'}
                          variant={category.productive ? 'filled' : 'outlined'}
                        />
                      </TableCell>
                      <TableCell>{category.departmentName ?? 'All departments'}</TableCell>
                      <TableCell align="right">
                        <Tooltip title="Edit">
                          <IconButton
                            size="small"
                            onClick={() => {
                              setEditingCategory(category);
                              setCategoryForm({
                                code: category.code,
                                name: category.name,
                                description: category.description ?? '',
                                categoryType: category.categoryType,
                                productive: category.productive,
                                departmentId: category.departmentId,
                                colourHex: category.colourHex ?? '',
                                displayOrder: category.displayOrder,
                              });
                              setCategoryDialog(true);
                            }}
                          >
                            <EditOutlined sx={{ fontSize: 17 }} />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Deactivate">
                          <IconButton size="small" onClick={() => removeCategory.mutate(category.id)}>
                            <DeleteOutlineOutlined sx={{ fontSize: 17 }} />
                          </IconButton>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          ))}

        {tab === 1 &&
          (templates.isLoading ? (
            <TableSkeleton rows={8} columns={5} />
          ) : (templates.data ?? []).length === 0 ? (
            <EmptyState
              title="No task templates"
              description="Templates give each department its own vocabulary in the work log."
            />
          ) : (
            <TableContainer sx={{ overflowX: 'auto' }}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Template</TableCell>
                    <TableCell>Department</TableCell>
                    <TableCell>Category</TableCell>
                    <TableCell>Default estimate</TableCell>
                    <TableCell>Priority</TableCell>
                    <TableCell>Recurrence</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {(templates.data ?? []).map((template) => (
                    <TableRow key={template.id} hover>
                      <TableCell>
                        <Typography sx={{ fontSize: 13, fontWeight: 600 }}>{template.name}</Typography>
                        {template.description && (
                          <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                            {template.description}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>{template.departmentName ?? 'All departments'}</TableCell>
                      <TableCell>{template.taskCategoryName}</TableCell>
                      <TableCell className="tabular">
                        {template.defaultEstimatedMinutes
                          ? formatDuration(template.defaultEstimatedMinutes)
                          : '—'}
                      </TableCell>
                      <TableCell sx={{ textTransform: 'capitalize' }}>
                        {template.defaultPriority.toLowerCase()}
                      </TableCell>
                      <TableCell sx={{ textTransform: 'capitalize' }}>
                        {template.recurrence.toLowerCase()}
                      </TableCell>
                      <TableCell align="right">
                        <Tooltip title="Edit">
                          <IconButton
                            size="small"
                            onClick={() => {
                              setEditingTemplate(template);
                              setTemplateForm({
                                departmentId: template.departmentId,
                                taskCategoryId: template.taskCategoryId,
                                name: template.name,
                                description: template.description ?? '',
                                defaultEstimatedMinutes: template.defaultEstimatedMinutes,
                                defaultPriority: template.defaultPriority,
                                recurrence: template.recurrence,
                                displayOrder: template.displayOrder,
                              });
                              setTemplateDialog(true);
                            }}
                          >
                            <EditOutlined sx={{ fontSize: 17 }} />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Deactivate">
                          <IconButton size="small" onClick={() => removeTemplate.mutate(template.id)}>
                            <DeleteOutlineOutlined sx={{ fontSize: 17 }} />
                          </IconButton>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          ))}

        {tab === 2 &&
          (projects.isLoading ? (
            <TableSkeleton rows={5} columns={5} />
          ) : (projects.data ?? []).length === 0 ? (
            <EmptyState title="No projects" description="Add a project so time can be attributed to it." />
          ) : (
            <TableContainer sx={{ overflowX: 'auto' }}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Project</TableCell>
                    <TableCell>Client</TableCell>
                    <TableCell>Department</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell align="right">Open tasks</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {(projects.data ?? []).map((project) => (
                    <TableRow key={project.id} hover>
                      <TableCell>
                        <Typography sx={{ fontSize: 13, fontWeight: 600 }}>{project.name}</Typography>
                        <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                          {project.code}
                          {project.billable ? ' · billable' : ''}
                        </Typography>
                      </TableCell>
                      <TableCell>{project.clientName ?? '—'}</TableCell>
                      <TableCell>{project.departmentName ?? 'All departments'}</TableCell>
                      <TableCell sx={{ textTransform: 'capitalize' }}>
                        {project.status.toLowerCase().replace('_', ' ')}
                      </TableCell>
                      <TableCell align="right" className="tabular">
                        {project.openTaskCount}
                      </TableCell>
                      <TableCell align="right">
                        <Tooltip title="Edit">
                          <IconButton
                            size="small"
                            onClick={() => {
                              setEditingProject(project);
                              setProjectForm({
                                code: project.code,
                                name: project.name,
                                description: project.description ?? '',
                                departmentId: project.departmentId,
                                clientName: project.clientName ?? '',
                                startDate: project.startDate,
                                endDate: project.endDate,
                                status: project.status,
                                billable: project.billable,
                              });
                              setProjectDialog(true);
                            }}
                          >
                            <EditOutlined sx={{ fontSize: 17 }} />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Deactivate">
                          <IconButton size="small" onClick={() => removeProject.mutate(project.id)}>
                            <DeleteOutlineOutlined sx={{ fontSize: 17 }} />
                          </IconButton>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          ))}
      </Card>

      {/* ---------------------------------------------------------- category */}
      <Dialog open={categoryDialog} onClose={() => setCategoryDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontSize: 16, fontWeight: 600 }}>
          {editingCategory ? 'Edit category' : 'Add category'}
        </DialogTitle>
        <DialogContent sx={{ display: 'grid', gap: 4, gridTemplateColumns: '1fr 1fr', pt: 2 }}>
          <TextField
            label="Code"
            required
            size="small"
            value={categoryForm.code}
            onChange={(event) =>
              setCategoryForm({ ...categoryForm, code: event.target.value.toUpperCase() })
            }
          />
          <TextField
            label="Name"
            required
            size="small"
            value={categoryForm.name}
            onChange={(event) => setCategoryForm({ ...categoryForm, name: event.target.value })}
          />
          <TextField
            select
            label="Type"
            size="small"
            value={categoryForm.categoryType}
            onChange={(event) => setCategoryForm({ ...categoryForm, categoryType: event.target.value })}
            helperText="Meeting and break time is reported separately"
          >
            {CATEGORY_TYPES.map((type) => (
              <MenuItem key={type} value={type}>
                {humanise(type)}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label="Department"
            size="small"
            value={categoryForm.departmentId ?? ''}
            onChange={(event) =>
              setCategoryForm({
                ...categoryForm,
                departmentId: event.target.value === '' ? null : Number(event.target.value),
              })
            }
          >
            <MenuItem value="">All departments</MenuItem>
            {(departments.data ?? []).map((option) => (
              <MenuItem key={option.id} value={option.id}>
                {option.label}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Colour"
            size="small"
            value={categoryForm.colourHex ?? ''}
            onChange={(event) => setCategoryForm({ ...categoryForm, colourHex: event.target.value })}
            placeholder="#4338CA"
            helperText="Used on the timeline"
          />
          <TextField
            label="Display order"
            type="number"
            size="small"
            value={categoryForm.displayOrder}
            onChange={(event) =>
              setCategoryForm({ ...categoryForm, displayOrder: Number(event.target.value) })
            }
          />
          <FormControlLabel
            sx={{ gridColumn: '1 / -1' }}
            control={
              <Switch
                checked={categoryForm.productive}
                onChange={(event) =>
                  setCategoryForm({ ...categoryForm, productive: event.target.checked })
                }
              />
            }
            label={
              <Box>
                <Typography variant="subtitle2">Counts as productive time</Typography>
                <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                  Feeds the productivity component of the performance score.
                </Typography>
              </Box>
            }
          />
        </DialogContent>
        <DialogActions sx={{ px: 6, pb: 5 }}>
          <Button variant="outlined" onClick={() => setCategoryDialog(false)}>
            Cancel
          </Button>
          <Button
            variant="contained"
            disabled={!categoryForm.code.trim() || !categoryForm.name.trim() || saveCategory.isPending}
            onClick={() => saveCategory.mutate(categoryForm)}
          >
            {saveCategory.isPending ? 'Saving…' : 'Save category'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* ---------------------------------------------------------- template */}
      <Dialog open={templateDialog} onClose={() => setTemplateDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontSize: 16, fontWeight: 600 }}>
          {editingTemplate ? 'Edit template' : 'Add template'}
        </DialogTitle>
        <DialogContent sx={{ display: 'grid', gap: 4, gridTemplateColumns: '1fr 1fr', pt: 2 }}>
          <TextField
            label="Name"
            required
            size="small"
            sx={{ gridColumn: '1 / -1' }}
            value={templateForm.name}
            onChange={(event) => setTemplateForm({ ...templateForm, name: event.target.value })}
          />
          <TextField
            select
            label="Department"
            size="small"
            value={templateForm.departmentId ?? ''}
            onChange={(event) =>
              setTemplateForm({
                ...templateForm,
                departmentId: event.target.value === '' ? null : Number(event.target.value),
              })
            }
          >
            <MenuItem value="">All departments</MenuItem>
            {(departments.data ?? []).map((option) => (
              <MenuItem key={option.id} value={option.id}>
                {option.label}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label="Category"
            required
            size="small"
            value={templateForm.taskCategoryId || ''}
            onChange={(event) =>
              setTemplateForm({ ...templateForm, taskCategoryId: Number(event.target.value) })
            }
          >
            {(categories.data ?? []).map((category) => (
              <MenuItem key={category.id} value={category.id}>
                {category.name}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label="Default priority"
            size="small"
            value={templateForm.defaultPriority ?? 'MEDIUM'}
            onChange={(event) =>
              setTemplateForm({ ...templateForm, defaultPriority: event.target.value as Priority })
            }
          >
            {PRIORITIES.map((priority) => (
              <MenuItem key={priority} value={priority}>
                {humanise(priority)}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label="Recurrence"
            size="small"
            value={templateForm.recurrence ?? 'NONE'}
            onChange={(event) =>
              setTemplateForm({ ...templateForm, recurrence: event.target.value as Recurrence })
            }
          >
            {RECURRENCES.map((recurrence) => (
              <MenuItem key={recurrence} value={recurrence}>
                {humanise(recurrence)}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Default estimate (minutes)"
            type="number"
            size="small"
            value={templateForm.defaultEstimatedMinutes ?? ''}
            onChange={(event) =>
              setTemplateForm({
                ...templateForm,
                defaultEstimatedMinutes: event.target.value === '' ? null : Number(event.target.value),
              })
            }
          />
          <TextField
            label="Display order"
            type="number"
            size="small"
            value={templateForm.displayOrder}
            onChange={(event) =>
              setTemplateForm({ ...templateForm, displayOrder: Number(event.target.value) })
            }
          />
          <TextField
            label="Description"
            size="small"
            multiline
            minRows={2}
            sx={{ gridColumn: '1 / -1' }}
            value={templateForm.description ?? ''}
            onChange={(event) => setTemplateForm({ ...templateForm, description: event.target.value })}
          />
        </DialogContent>
        <DialogActions sx={{ px: 6, pb: 5 }}>
          <Button variant="outlined" onClick={() => setTemplateDialog(false)}>
            Cancel
          </Button>
          <Button
            variant="contained"
            disabled={!templateForm.name.trim() || !templateForm.taskCategoryId || saveTemplate.isPending}
            onClick={() => saveTemplate.mutate(templateForm)}
          >
            {saveTemplate.isPending ? 'Saving…' : 'Save template'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* ----------------------------------------------------------- project */}
      <Dialog open={projectDialog} onClose={() => setProjectDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontSize: 16, fontWeight: 600 }}>
          {editingProject ? 'Edit project' : 'Add project'}
        </DialogTitle>
        <DialogContent sx={{ display: 'grid', gap: 4, gridTemplateColumns: '1fr 1fr', pt: 2 }}>
          <TextField
            label="Code"
            required
            size="small"
            value={projectForm.code}
            onChange={(event) =>
              setProjectForm({ ...projectForm, code: event.target.value.toUpperCase() })
            }
          />
          <TextField
            label="Name"
            required
            size="small"
            value={projectForm.name}
            onChange={(event) => setProjectForm({ ...projectForm, name: event.target.value })}
          />
          <TextField
            label="Client"
            size="small"
            value={projectForm.clientName ?? ''}
            onChange={(event) => setProjectForm({ ...projectForm, clientName: event.target.value })}
          />
          <TextField
            select
            label="Department"
            size="small"
            value={projectForm.departmentId ?? ''}
            onChange={(event) =>
              setProjectForm({
                ...projectForm,
                departmentId: event.target.value === '' ? null : Number(event.target.value),
              })
            }
          >
            <MenuItem value="">All departments</MenuItem>
            {(departments.data ?? []).map((option) => (
              <MenuItem key={option.id} value={option.id}>
                {option.label}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Start date"
            type="date"
            size="small"
            value={projectForm.startDate ?? ''}
            onChange={(event) =>
              setProjectForm({ ...projectForm, startDate: event.target.value || null })
            }
            InputLabelProps={{ shrink: true }}
          />
          <TextField
            label="End date"
            type="date"
            size="small"
            value={projectForm.endDate ?? ''}
            onChange={(event) => setProjectForm({ ...projectForm, endDate: event.target.value || null })}
            InputLabelProps={{ shrink: true }}
          />
          <FormControlLabel
            sx={{ gridColumn: '1 / -1' }}
            control={
              <Switch
                checked={projectForm.billable}
                onChange={(event) => setProjectForm({ ...projectForm, billable: event.target.checked })}
              />
            }
            label={<Typography variant="body2">Billable to a client</Typography>}
          />
        </DialogContent>
        <DialogActions sx={{ px: 6, pb: 5 }}>
          <Button variant="outlined" onClick={() => setProjectDialog(false)}>
            Cancel
          </Button>
          <Button
            variant="contained"
            disabled={!projectForm.code.trim() || !projectForm.name.trim() || saveProject.isPending}
            onClick={() => saveProject.mutate(projectForm)}
          >
            {saveProject.isPending ? 'Saving…' : 'Save project'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

function humanise(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}
